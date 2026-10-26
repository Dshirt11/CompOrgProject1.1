import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * PCI devices found by Linux, Windows, or macOS.
 *
 * Linux devices are read from /sys/bus/pci/devices. Windows devices are read
 * from the Plug and Play registry, and macOS devices from system_profiler.
 * Devices are sorted by address and cached on first use; call rescan() to read
 * them again.
 *
 * Vendor and device IDs are numbers (for example 0x8086 is Intel). The class code
 * is 24 bits: base class, sub class and interface. See idLookup to turn these
 * numbers into names.
 */
public class pciInfo
{
    private static final Path ROOT = sysFile.path("/sys/bus/pci/devices");
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private static final boolean MACOS = System.getProperty("os.name").startsWith("Mac");
    private static List<Device> devices;

    private static final class Device
    {
        final Path path;
        final String address;
        final int bus;
        final int device;
        final int function;
        final int vendorId;
        final int deviceId;
        final int classCode;

        Device(Path path)
        {
            this.path = path;
            this.address = path.getFileName().toString();
            this.bus = -1;
            this.device = -1;
            this.function = -1;
            this.vendorId = -1;
            this.deviceId = -1;
            this.classCode = -1;
        }

        Device(String address, int bus, int device, int function,
               int vendorId, int deviceId, int classCode)
        {
            this.path = null;
            this.address = address;
            this.bus = bus;
            this.device = device;
            this.function = function;
            this.vendorId = vendorId;
            this.deviceId = deviceId;
            this.classCode = classCode;
        }

        int vendorId()
        {
            return path == null ? vendorId : (int) sysFile.number(path.resolve("vendor"));
        }

        int deviceId()
        {
            return path == null ? deviceId : (int) sysFile.number(path.resolve("device"));
        }

        int classCode()
        {
            return path == null ? classCode : (int) sysFile.number(path.resolve("class"));
        }
    }

    private static List<Device> list()
    {
        if (devices == null) {
            List<Device> found;
            if (WINDOWS) {
                found = windowsDevices();
            } else if (MACOS) {
                found = macDevices();
            } else {
                found = new ArrayList<>();
                if (Files.isDirectory(ROOT)) {
                    try (DirectoryStream<Path> dir = Files.newDirectoryStream(ROOT)) {
                        for (Path p : dir)
                            found.add(new Device(p));
                    } catch (IOException e) {
                        // leave the list as it is
                    }
                }
            }
            found.sort((a, b) -> a.address.compareTo(b.address));
            devices = found;
        }
        return devices;
    }

    private static List<Device> macDevices()
    {
        try {
            Process process = new ProcessBuilder("system_profiler", "-xml", "SPPCIDataType")
                .redirectErrorStream(true)
                .start();
            byte[] output = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode != 0)
                throw new IllegalStateException("macOS PCI device query failed: "
                                                + new String(output, StandardCharsets.UTF_8).strip());

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            NodeList dictionaries = factory.newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(output)).getElementsByTagName("dict");

            List<Device> found = new ArrayList<>();
            for (int i = 0; i < dictionaries.getLength(); i++) {
                Element dictionary = (Element) dictionaries.item(i);
                String vendorId = plistValue(dictionary, "sppci_vendor-id");
                String deviceId = plistValue(dictionary, "sppci_device-id");
                if (vendorId == null || deviceId == null)
                    continue;

                int bus = decimal(plistValue(dictionary, "sppci_bus"));
                int slot = decimal(plistValue(dictionary, "sppci_device"));
                int function = decimal(plistValue(dictionary, "sppci_function"));
                int vendor = hexadecimal(vendorId);
                int device = hexadecimal(deviceId);
                int classCode = hexadecimal(plistValue(dictionary, "sppci_class-code"));
                String address = bus < 0 || slot < 0 || function < 0
                    ? plistValue(dictionary, "_name")
                    : String.format("0000:%02x:%02x.%x", bus, slot, function);
                if (address == null)
                    address = "unknown";
                found.add(new Device(address, bus, slot, function, vendor, device, classCode));
            }
            return found;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Could not parse macOS PCI device data", e);
        } catch (IOException e) {
            throw new IllegalStateException("Could not run system_profiler to query macOS PCI devices", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying macOS PCI devices", e);
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

    private static int decimal(String value)
    {
        if (value == null)
            return -1;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int hexadecimal(String value)
    {
        if (value == null)
            return -1;
        String digits = value.startsWith("0x") || value.startsWith("0X")
            ? value.substring(2) : value;
        try {
            return Integer.parseInt(digits, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static List<Device> windowsDevices()
    {
        String script =
            "$ErrorActionPreference = 'Stop'\n" +
            "$ProgressPreference = 'SilentlyContinue'\n" +
            "$root = 'HKLM:\\SYSTEM\\CurrentControlSet\\Enum\\PCI'\n" +
            "try {\n" +
            "  foreach ($vendorKey in Get-ChildItem -LiteralPath $root) {\n" +
            "    foreach ($instanceKey in Get-ChildItem -LiteralPath $vendorKey.PSPath) {\n" +
            "      $properties = Get-ItemProperty -LiteralPath $instanceKey.PSPath\n" +
            "      $vendor = $null; $device = $null; $class = $null\n" +
            "      foreach ($id in (@($properties.HardwareID) + @($properties.CompatibleIDs))) {\n" +
            "        if (!$vendor -and $id -match 'VEN_([0-9A-Fa-f]{4})') { $vendor = $Matches[1] }\n" +
            "        if (!$device -and $id -match 'DEV_([0-9A-Fa-f]{4})') { $device = $Matches[1] }\n" +
            "        if (!$class -and $id -match 'CC_([0-9A-Fa-f]{6})') { $class = $Matches[1] }\n" +
            "      }\n" +
            "      if ($vendor -and $device) {\n" +
            "        $bus = -1; $slot = -1; $function = -1\n" +
            "        $location = [string]$properties.LocationInformation\n" +
            "        if ($location -match '\\((\\d+),(\\d+),(\\d+)\\)\\s*$') {\n" +
            "          $bus = $Matches[1]; $slot = $Matches[2]; $function = $Matches[3]\n" +
            "        }\n" +
            "        $instanceId = 'PCI\\' + $vendorKey.PSChildName + '\\' + $instanceKey.PSChildName\n" +
            "        [Console]::Out.WriteLine(\"$bus`t$slot`t$function`t$vendor`t$device`t$class`t$instanceId\")\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "} catch { [Console]::Error.WriteLine($_); exit 1 }\n";

        String encodedScript = Base64.getEncoder().encodeToString(
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
                throw new IllegalStateException("Windows PCI device query failed: " + output.strip());

            List<Device> found = new ArrayList<>();
            for (String line : output.split("\\R")) {
                if (line.isBlank())
                    continue;
                String[] fields = line.split("\\t", -1);
                if (fields.length != 7)
                    throw new IllegalStateException("Unexpected output from Windows PCI device query: " + line);
                int bus = Integer.parseInt(fields[0]);
                int slot = Integer.parseInt(fields[1]);
                int function = Integer.parseInt(fields[2]);
                int vendor = Integer.parseInt(fields[3], 16);
                int device = Integer.parseInt(fields[4], 16);
                int classCode = fields[5].isEmpty() ? -1 : Integer.parseInt(fields[5], 16);
                String address = bus < 0 || slot < 0 || function < 0
                    ? fields[6]
                    : String.format("0000:%02x:%02x.%x", bus, slot, function);
                found.add(new Device(address, bus, slot, function, vendor, device, classCode));
            }
            return found;
        } catch (IOException e) {
            throw new IllegalStateException("Could not run PowerShell to query Windows PCI devices", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while querying Windows PCI devices", e);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid data returned by the Windows PCI device query", e);
        }
    }

    /** Read the list of devices again (useful if hardware may have changed). */
    public static void rescan()
    {
        devices = null;
    }

    /** How many PCI devices there are. */
    public static int getCount()
    {
        return list().size();
    }

    /** The address as text, for example "0000:00:02.0". */
    public static String getAddress(int index)
    {
        return list().get(index).address;
    }

    /** The PCI bus number, or -1 if Windows did not report the location. */
    public static int getBus(int index)
    {
        Device device = list().get(index);
        return device.path == null ? device.bus
            : Integer.parseInt(device.address.substring(5, 7), 16);
    }

    /** The device number on the bus, or -1 if Windows did not report the location. */
    public static int getDevice(int index)
    {
        Device device = list().get(index);
        return device.path == null ? device.device
            : Integer.parseInt(device.address.substring(8, 10), 16);
    }

    /** The PCI function number, or -1 if Windows did not report the location. */
    public static int getFunction(int index)
    {
        Device device = list().get(index);
        return device.path == null ? device.function
            : Integer.parseInt(device.address.substring(11, 12), 16);
    }

    /** The vendor ID, for example 0x8086, or -1 if it cannot be read. */
    public static int getVendorId(int index)
    {
        return list().get(index).vendorId();
    }

    /** The device ID, or -1 if it cannot be read. */
    public static int getDeviceId(int index)
    {
        return list().get(index).deviceId();
    }

    /** The 24-bit class code, for example 0x060000 for a host bridge, or -1. */
    public static int getClassCode(int index)
    {
        return list().get(index).classCode();
    }
}
