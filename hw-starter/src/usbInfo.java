import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * USB devices found by Linux.
 *
 * Devices are read from /sys/bus/usb/devices and cached on first use; call
 * rescan() to read them again.
 */
public class usbInfo
{
    // sysFile can redirect this Linux path to copied test data via hw.root.
    private static final Path ROOT = sysFile.path("/sys/bus/usb/devices");

    // Keep a single snapshot until callers explicitly request rescan().
    private static List<UsbDevice> devices;

    /** One USB device directory in sysfs. */
    private static final class UsbDevice
    {
        final Path path;
        final String name;

        UsbDevice(Path path)
        {
            this.path = path;
            this.name = path.getFileName().toString();
        }

        int vendorId()
        {
            return hex(path.resolve("idVendor"));
        }

        int productId()
        {
            return hex(path.resolve("idProduct"));
        }

        String manufacturer()
        {
            return sysFile.text(path.resolve("manufacturer"));
        }

        String product()
        {
            return sysFile.text(path.resolve("product"));
        }

        int bus()
        {
            // These numeric fields are populated by Linux for USB device nodes.
            return (int) sysFile.number(path.resolve("busnum"));
        }

        int device()
        {
            return (int) sysFile.number(path.resolve("devnum"));
        }
    }

    private static List<UsbDevice> list()
    {
        if (devices == null) {
            // Cache the discovered list so repeated getters use the same snapshot.
            devices = linuxDevices();
        }
        return devices;
    }

    private static List<UsbDevice> linuxDevices()
    {
        List<UsbDevice> found = new ArrayList<>();
        if (Files.isDirectory(ROOT)) {
            try (DirectoryStream<Path> dir = Files.newDirectoryStream(ROOT)) {
                // Root hubs/interfaces without an idVendor file are not USB devices.
                for (Path path : dir)
                    if (Files.exists(path.resolve("idVendor")))
                        found.add(new UsbDevice(path));
            } catch (IOException e) {
                // Retain any entries already discovered if reading the directory fails.
                // leave the list as it is
            }
        }
        // Put devices in bus/device order; missing numbers sort after known ones.
        found.sort(Comparator.comparingInt((UsbDevice device) -> sortNumber(device.bus()))
                             .thenComparingInt(device -> sortNumber(device.device())));
        return found;
    }

    private static int sortNumber(int number)
    {
        // Integer.MAX_VALUE makes unavailable values sort to the end.
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

    /** The bus number. */
    public static int getBusNumber(int index)
    {
        return list().get(index).bus();
    }

    /** The device number. */
    public static int getDeviceNumber(int index)
    {
        return list().get(index).device();
    }

    private static int hex(Path file)
    {
        // USB IDs in sysfs are hexadecimal without a 0x prefix.
        String value = sysFile.text(file);
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
