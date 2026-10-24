import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A short example that shows how to call each provided class and prints a
 * formatted hardware summary. It is NOT a solution to the assignment: you
 * still have to measure load, store the devices, sort them, analyse them and
 * present the results.
 *
 * Build:  javac *.java
 * Run:    java demo        (run it from the folder that contains the ids folder)
 */
public class demo
{
    public static void main(String[] args) throws Exception
    {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        boolean macos = System.getProperty("os.name").startsWith("Mac");
        if (windows || macos) {
            System.out.println("CPU and memory information require Linux /proc files.");
            System.out.println("USB information requires Linux /sys files.");
        } else {
            System.out.println("cores:  " + cpuInfo.getCount());
            System.out.println("model:  " + cpuInfo.getModel());
            System.out.println("core 0: busy=" + cpuInfo.getBusy(0) + " idle=" + cpuInfo.getIdle(0));
            System.out.println("memory: total=" + memInfo.getTotal() + " KiB, used=" + memInfo.getUsed() + " KiB");
        }

        idLookup pci = idLookup.pci();
        int pciCount = pciInfo.getCount();
        List<PciDevice> pciDevices = new ArrayList<>();
        for (int i = 0; i < pciCount; i++) {
            pciDevices.add(new PciDevice(pciInfo.getBus(i), pciInfo.getDevice(i), pciInfo.getFunction(i),
                                         pciInfo.getVendorId(i), pciInfo.getDeviceId(i),
                                         pciInfo.getClassCode(i)));
        }

        List<PciDevice> byVendor = new ArrayList<>(pciDevices);
        byVendor.sort(Comparator
            .comparing((PciDevice device) -> pciNameOrId(
                device.vendorId, pci.vendorName(device.vendorId)), String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(device -> device.vendorId)
            .thenComparingInt(device -> device.deviceId));
        printPciDevices("PCI DEVICES SORTED BY VENDOR NAME", byVendor, pci);

        List<PciDevice> byAddress = new ArrayList<>(pciDevices);
        byAddress.sort(Comparator
            .comparingInt((PciDevice device) -> sortNumber(device.bus))
            .thenComparingInt(device -> sortNumber(device.device))
            .thenComparingInt(device -> sortNumber(device.function)));
        printPciDevices("PCI DEVICES SORTED BY BUS, DEVICE, FUNCTION", byAddress, pci);

        Map<String, Integer> devicesPerVendor = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (PciDevice device : pciDevices) {
            String vendor = pciNameOrId(device.vendorId, pci.vendorName(device.vendorId));
            devicesPerVendor.merge(vendor, 1, Integer::sum);
        }
        System.out.println();
        System.out.println("PCI DEVICE COUNT BY VENDOR");
        for (Map.Entry<String, Integer> entry : devicesPerVendor.entrySet())
            System.out.printf("  %-40s %d%n", entry.getKey(), entry.getValue());
        System.out.println("Total PCI devices: " + pciDevices.size());

        if (windows || macos)
            return;

        idLookup usb = idLookup.usb();
        int usbCount = usbInfo.getCount();
        System.out.println("USB devices: " + usbCount);
        for (int i = 0; i < usbCount; i++) {
            int vendor = usbInfo.getVendorId(i);
            int product = usbInfo.getProductId(i);
            System.out.printf("  Bus %03d Device %03d: ID %s:%s  %s %s%n",
                              usbInfo.getBusNumber(i), usbInfo.getDeviceNumber(i),
                              hexId(vendor), hexId(product),
                              nameOrUnknown(usb.vendorName(vendor)),
                              nameOrUnknown(usb.deviceName(vendor, product)));
        }
    }

    private static void printPciDevices(String heading, List<PciDevice> devices, idLookup pci)
    {
        System.out.println();
        System.out.println(heading + " (" + devices.size() + ")");
        int vendorWidth = "Vendor".length();
        int deviceNameWidth = "Device".length();
        for (PciDevice device : devices) {
            vendorWidth = Math.max(vendorWidth,
                pciNameOrId(device.vendorId, pci.vendorName(device.vendorId)).length());
            deviceNameWidth = Math.max(deviceNameWidth,
                pciNameOrId(device.deviceId,
                            pci.deviceName(device.vendorId, device.deviceId)).length());
        }
        String columns = "%-4s %-5s %-7s %-9s %-" + vendorWidth + "s %-"
                       + deviceNameWidth + "s %-10s %s%n";
        System.out.printf(columns, "No.", "Bus", "Device", "Function", "Vendor", "Device",
                          "Class code", "Class");
        System.out.printf(columns, "---", "---", "------", "--------", "------", "------",
                          "----------", "-----");
        for (int i = 0; i < devices.size(); i++) {
            PciDevice device = devices.get(i);
            String vendor = pciNameOrId(device.vendorId, pci.vendorName(device.vendorId));
            String description = pciNameOrId(device.deviceId,
                                             pci.deviceName(device.vendorId, device.deviceId));
            System.out.printf(columns,
                              i + 1, numberOrUnknown(device.bus), numberOrUnknown(device.device),
                              numberOrUnknown(device.function), vendor, description,
                              hexClass(device.classCode),
                              nameOrUnknown(pci.className(device.classCode)));
        }
    }

    private static String hexId(int id)
    {
        return id < 0 ? "unknown" : String.format("%04x", id);
    }

    private static String hexClass(int classCode)
    {
        return classCode < 0 ? "unknown" : String.format("%06x", classCode);
    }

    private static String pciNameOrId(int id, String name)
    {
        if (name != null)
            return name;
        return id < 0 ? "unknown" : String.format("0x%04x", id);
    }

    private static String numberOrUnknown(int number)
    {
        return number < 0 ? "unknown" : Integer.toString(number);
    }

    private static int sortNumber(int number)
    {
        return number < 0 ? Integer.MAX_VALUE : number;
    }

    private static String nameOrUnknown(String name)
    {
        return name == null ? "unknown" : name;
    }

    private static final class PciDevice
    {
        final int bus;
        final int device;
        final int function;
        final int vendorId;
        final int deviceId;
        final int classCode;

        PciDevice(int bus, int device, int function, int vendorId, int deviceId, int classCode)
        {
            this.bus = bus;
            this.device = device;
            this.function = function;
            this.vendorId = vendorId;
            this.deviceId = deviceId;
            this.classCode = classCode;
        }
    }
}
