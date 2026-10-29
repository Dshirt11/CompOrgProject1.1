import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// Prints the PCI device list sorted in the two ways the brief asks for:
//
//   1. alphabetically by vendor name (A to Z), and
//   2. numerically by bus, then device, then function.
//
// The caller gives this class a list of Entry objects (one per PCI device) and
// an idLookup, which is used to turn vendor IDs into names. This class does
// not read any files itself.
public class pciSorter
{
    // One PCI device. It is a simple holder for the numbers that were read
    // from /sys/bus/pci/devices.
    public static class Entry
    {
        public final int bus;
        public final int device;
        public final int function;
        public final int vendorId;
        public final int deviceId;
        public final int classCode;

        public Entry(int bus, int device, int function, int vendorId, int deviceId, int classCode)
        {
            this.bus = bus;
            this.device = device;
            this.function = function;
            this.vendorId = vendorId;
            this.deviceId = deviceId;
            this.classCode = classCode;
        }
    }

    // Sorts by vendor name, A to Z, ignoring upper and lower case.
    // If two devices have the same vendor name, the one with the smaller
    // bus/device/function comes first so the order is always the same.
    private static class ByVendorName implements Comparator<Entry>
    {
        private final idLookup names;

        ByVendorName(idLookup names)
        {
            this.names = names;
        }

        @Override
        public int compare(Entry a, Entry b)
        {
            // Compare the two vendor names as text, ignoring case.
            int result = vendorText(a, names).compareToIgnoreCase(vendorText(b, names));
            if (result != 0)
                return result;

            // Same vendor, so fall back to the numeric address order.
            return new ByAddress().compare(a, b);
        }
    }

    // Sorts by bus number first, then device number, then function number.
    // These are compared as numbers (so bus 2 comes before bus 10), not as text.
    private static class ByAddress implements Comparator<Entry>
    {
        @Override
        public int compare(Entry a, Entry b)
        {
            // Compare the bus numbers first.
            if (a.bus != b.bus)
                return Integer.compare(a.bus, b.bus);

            // Same bus, so compare the device numbers.
            if (a.device != b.device)
                return Integer.compare(a.device, b.device);

            // Same bus and device, so compare the function numbers.
            return Integer.compare(a.function, b.function);
        }
    }

    // Prints both sorted lists to the given writer.
    // The original list is not changed: each sort works on its own copy.
    public static void printBothSorts(PrintWriter out, List<Entry> devices, idLookup names)
    {
        // Make two separate copies so the two sorts do not disturb each other.
        List<Entry> byVendor = new ArrayList<>(devices);
        List<Entry> byAddress = new ArrayList<>(devices);

        // Sort the first copy A to Z by vendor name.
        Collections.sort(byVendor, new ByVendorName(names));

        // Sort the second copy by bus, device and function number.
        Collections.sort(byAddress, new ByAddress());

        out.println();
        out.println("PCI DEVICES SORTED ALPHABETICALLY BY VENDOR NAME");
        printTable(out, byVendor, names);

        out.println();
        out.println("PCI DEVICES SORTED NUMERICALLY BY BUS, DEVICE, FUNCTION");
        printTable(out, byAddress, names);
    }

    // Prints one table of devices, one device per line.
    private static void printTable(PrintWriter out, List<Entry> devices, idLookup names)
    {
        if (devices.isEmpty()) {
            out.println("  No PCI devices found.");
            return;
        }

        out.println("Bus  Dev  Fn   Vendor ID  Device ID  Class     Vendor name");
        out.println("---  ---  ---  ---------  ---------  --------  -----------");
        for (Entry d : devices) {
            out.printf("%-3s  %-3s  %-3s  %-9s  %-9s  %-8s  %s%n",
                       number(d.bus), number(d.device), number(d.function),
                       hex(d.vendorId, 4), hex(d.deviceId, 4), hex(d.classCode, 6),
                       vendorText(d, names));
        }
    }

    // The text used for a vendor: its name if the ID is in pci.ids,
    // otherwise the raw ID such as 0x1234.
    private static String vendorText(Entry d, idLookup names)
    {
        String name = names.vendorName(d.vendorId);
        if (name != null)
            return name;
        if (d.vendorId < 0)
            return "unknown";
        return "0x" + hex(d.vendorId, 4);
    }

    // A number as text, or "?" if it could not be read (the readers give -1).
    private static String number(int value)
    {
        return value < 0 ? "?" : Integer.toString(value);
    }

    // A hexadecimal number padded with zeros, or "?" if it could not be read.
    private static String hex(int value, int digits)
    {
        if (value < 0)
            return "?";
        return String.format(Locale.ROOT, "%0" + digits + "x", value);
    }
}