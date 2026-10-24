import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * USB devices found by Linux, Windows, or macOS.
 *
 * Linux devices are read from /sys/bus/usb/devices. Windows devices are read
 * from Plug and Play, and macOS devices from system_profiler. Bus and device
 * numbers are only available from the Linux sysfs reader. The list is cached
 * on first use; call rescan() to read it again.
 */
public class usbInfo
{
    private static final Path ROOT = sysFile.path("/sys/bus/usb/devices");
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final boolean MACOS = System.getProperty("os.name").startsWith("Mac");
    private static final Pattern WINDOWS_USB_ID =
        Pattern.compile("VID_([0-9A-Fa-f]{4})&PID_([0-9A-Fa-f]{4})");
    private static List<UsbDevice> devices;

    private static final class UsbDevice
    {
        final Path path;
        final String name;
        final String manufacturer;
        final String product;
        final int bus;
        final int device;
        final int vendorId;
        final int productId;

        UsbDevice(Path path)
        {
            this.path = path;
            this.name = path.getFileName().toString();
            this.manufacturer = "";
            this.product = "";
            this.bus = -1;
            this.device = -1;
            this.vendorId = -1;
            this.productId = -1;
        }

        UsbDevice(String name, String manufacturer, String product, int bus, int device,
                  int vendorId, int productId)
        {
            this.path = null;
            this.name = name;
            this.manufacturer = manufacturer;
            this.product = product;
            this.bus = bus;
            this.device = device;
            this.vendorId = vendorId;
            this.productId = productId;
        }

        int vendorId()
        {
            return path == null ? vendorId : hex(path.resolve("idVendor"));
        }

        int productId()
        {
            return path == null ? productId : hex(path.resolve("idProduct"));
        }

        String manufacturer()
        {
            return path == null ? manufacturer : sysFile.text(path.resolve("manufacturer"));
        }

        String product()
        {
            return path == null ? product : sysFile.text(path.resolve("product"));
        }

        int bus()
        {
            return path == null ? bus : (int) sysFile.number(path.resolve("busnum"));
        }

        int device()
        {
            return path == null ? device : (int) sysFile.number(path.resolve("devnum"));
        }
    }

    private static List<UsbDevice> list()
    {
        if (devices == null) {
            List<UsbDevice> found;
            if (WINDOWS) {
                found = windowsDevices();
                found.sort(Comparator
                    .comparing((UsbDevice device) -> device.manufacturer,
                               String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(device -> device.product, String.CASE_INSENSITIVE_ORDER));
            } else if (MACOS) {
                found = macDevices();
                found.sort(Comparator.comparing((UsbDevice device) -> device.name,
                                                String.CASE_INSENSITIVE_ORDER));
            } else {
                found = linuxDevices();
            }
            devices = found;
        }
        return devices;
    }

    private static List<UsbDevice> linuxDevices()
    {
        List<UsbDevice> found = new ArrayList<>();
        if (Files.isDirectory(ROOT)) {
            try (DirectoryStream<Path> dir = Files.newDirectoryStream(ROOT)) {
                for (Path path : dir)
                    if (Files.exists(path.resolve("idVendor")))
                        found.add(new UsbDevice(path));
            } catch (IOException e) {
                // leave the list as it is
            }
        }
        found.sort(Comparator.comparingInt((UsbDevice device) -> sortNumber(device.bus()))
                             .thenComparingInt(device -> sortNumber(device.device())));
        return found;
    }

    private static List<UsbDevice> windowsDevices()
    {
        String script =
            "$ErrorActionPreference = 'Stop'\n" +
            "$ProgressPreference = 'SilentlyContinue'\n" +
            "try {\n" +
            "  Get-CimInstance -ClassName Win32_PnPEntity | Where-Object {\n" +
            "    $_.PNPDeviceID -match '^USB\\\\VID_([0-9A-Fa-f]{4})&PID_([0-9A-Fa-f]{4})' -and\n" +
            "    $_.PNPDeviceID -notmatch '&MI_'\n" +
            "  } | ForEach-Object {\n" +
            "    $match = [regex]::Match($_.PNPDeviceID, 'VID_([0-9A-Fa-f]{4})&PID_([0-9A-Fa-f]{4})')\n" +
            "    $manufacturer = ([string]$_.Manufacturer) -replace '[\\t\\r\\n]', ' '\n" +
            "    $product = ([string]$_.Name) -replace '[\\t\\r\\n]', ' '\n" +
            "    [Console]::Out.WriteLine(\"$($match.Groups[1].Value)`t$($match.Groups[2].Value)`t$manufacturer`t$product\")\n" +
            "  }\n" +
            "} catch { [Console]::Error.WriteLine($_); exit 1 }\n";
        String encodedScript = java.util.Base64.getEncoder().encodeToString(
            script.getBytes(StandardCharsets.UTF_16LE));
        try {
            Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile",
                                                 "-NonInteractive", "-OutputFormat", "Text",
                                                 "-EncodedCommand", encodedScript)
                .redirectErrorStream(true)
                .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0)
                throw new IllegalStateException("Windows USB device query failed: " + output.strip());

            List<UsbDevice> found = new ArrayList<>();
            for (String line : output.split("\\R")) {
                if (line.isBlank())
                    continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length != 4)
                    throw new IllegalStateException("Unexpected output from Windows USB device query: " + line);
                found.add(new UsbDevice(fields[0] + ":" + fields[1], fields[2], fields[3],
                                        -1, -1, hexadecimal(fields[0]), hexadecimal(fields[1])));
            }
            return found;
        } catch (IOException e) {
            throw new IllegalStateException("Could not run PowerShell to query Windows USB devices", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying Windows USB devices", e);
        }
    }

    private static List<UsbDevice> macDevices()
    {
        try {
            Process process = new ProcessBuilder("system_profiler", "-xml", "SPUSBDataType")
                .redirectErrorStream(true)
                .start();
            byte[] output = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode != 0)
                throw new IllegalStateException("macOS USB device query failed: "
                                                + new String(output, StandardCharsets.UTF_8).strip());

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            NodeList dictionaries = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(output)).getElementsByTagName("dict");

            List<UsbDevice> found = new ArrayList<>();
            for (int i = 0; i < dictionaries.getLength(); i++) {
                Element dictionary = (Element) dictionaries.item(i);
                String vendor = plistValue(dictionary, "vendor_id");
                String productId = plistValue(dictionary, "product_id");
                if (vendor == null || productId == null)
                    continue;
                String name = plistValue(dictionary, "_name");
                String manufacturer = plistValue(dictionary, "manufacturer");
                found.add(new UsbDevice(name == null ? "" : name,
                                        manufacturer == null ? "" : manufacturer,
                                        name == null ? "" : name, -1, -1,
                                        hexadecimal(vendor), hexadecimal(productId)));
            }
            return found;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Could not parse macOS USB device data", e);
        } catch (IOException e) {
            throw new IllegalStateException("Could not run system_profiler to query macOS USB devices", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying macOS USB devices", e);
        }
    }

    private static String plistValue(Element dictionary, String key)
    {
        NodeList children = dictionary.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element && node.getNodeName().equals("key")
                && node.getTextContent().equals(key)) {
                for (Node value = node.getNextSibling(); value != null; value = value.getNextSibling())
                    if (value instanceof Element)
                        return value.getTextContent();
                return null;
            }
        }
        return null;
    }

    private static int hexadecimal(String value)
    {
        Matcher matcher = Pattern.compile("(?:0[xX])?([0-9A-Fa-f]{1,4})").matcher(value);
        if (!matcher.find())
            return -1;
        try {
            return Integer.parseInt(matcher.group(1), 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int sortNumber(int number)
    {
        return number < 0 ? Integer.MAX_VALUE : number;
    }

    /** Read the list of devices again. */
    public static void rescan()
    {
        devices = null;
    }

    /** How many USB devices there are. */
    public static int getCount()
    {
        return list().size();
    }

    /** The name or identifier given to the device by its operating system. */
    public static String getName(int index)
    {
        return list().get(index).name;
    }

    /** The vendor ID, for example 0x046d, or -1 if unavailable. */
    public static int getVendorId(int index)
    {
        return list().get(index).vendorId();
    }

    /** The product ID, or -1 if unavailable. */
    public static int getProductId(int index)
    {
        return list().get(index).productId();
    }

    /** The manufacturer reported by the device or operating system, or "". */
    public static String getManufacturer(int index)
    {
        return list().get(index).manufacturer();
    }

    /** The product text reported by the device or operating system, or "". */
    public static String getProduct(int index)
    {
        return list().get(index).product();
    }

    /** The bus number, or -1 when unavailable on this operating system. */
    public static int getBusNumber(int index)
    {
        return list().get(index).bus();
    }

    /** The device number, or -1 when unavailable on this operating system. */
    public static int getDeviceNumber(int index)
    {
        return list().get(index).device();
    }

    private static int hex(Path file)
    {
        String value = sysFile.text(file);
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
