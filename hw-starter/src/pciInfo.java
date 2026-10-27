import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PCI devices found by Linux.
 *
 * Devices are read from /sys/bus/pci/devices, sorted by address, and cached
 * on first use; call rescan() to read them again.
 *
 * Vendor and device IDs are numbers (for example 0x8086 is Intel). The class code
 * is 24 bits: base class, sub class and interface. See idLookup to turn these
 * numbers into names.
 */
public class pciInfo
{
    // sysFile also allows this Linux path to be redirected for fixture testing.
    private static final Path ROOT = sysFile.path("/sys/bus/pci/devices");

    // Read the directory only on first access; rescan() clears this cache.
    private static List<Device> devices;

    /** One PCI device directory and its PCI address. */
    private static final class Device
    {
        final Path path;
        final String address;

        Device(Path path)
        {
            this.path = path;
            this.address = path.getFileName().toString();
        }

        int vendorId()
        {
            // sysfs stores these identifiers as hexadecimal strings such as 0x8086.
            return (int) sysFile.number(path.resolve("vendor"));
        }

        int deviceId()
        {
            return (int) sysFile.number(path.resolve("device"));
        }

        int classCode()
        {
            return (int) sysFile.number(path.resolve("class"));
        }
    }

    private static List<Device> list()
    {
        if (devices == null) {
            List<Device> found = new ArrayList<>();
            if (Files.isDirectory(ROOT)) {
                try (DirectoryStream<Path> dir = Files.newDirectoryStream(ROOT)) {
                    // Each entry in this directory represents one PCI function.
                    for (Path p : dir)
                        found.add(new Device(p));
                } catch (IOException e) {
                    // Keep the readable entries already collected if listing fails.
                    // leave the list as it is
                }
            }
            // Stable address order makes reports and tables easier to compare.
            found.sort((a, b) -> a.address.compareTo(b.address));
            devices = found;
        }
        return devices;
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

    /** The PCI bus number. */
    public static int getBus(int index)
    {
        // PCI addresses look like "0000:00:02.0"; positions 5-6 are the bus.
        return Integer.parseInt(list().get(index).address.substring(5, 7), 16);
    }

    /** The device number on the bus. */
    public static int getDevice(int index)
    {
        // Positions 8-9 are the slot/device number in the PCI address.
        return Integer.parseInt(list().get(index).address.substring(8, 10), 16);
    }

    /** The PCI function number. */
    public static int getFunction(int index)
    {
        // The character after the dot is the PCI function number.
        return Integer.parseInt(list().get(index).address.substring(11, 12), 16);
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
