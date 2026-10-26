/* numbers and a few names. It is NOT a solution to the assignment: you still
 * have to measure load, store the devices, sort them, analyse them and present
 * the results.
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A short example that shows how to call each provided class and prints a
 * formatted hardware summary. It is NOT a solution to the assignment: you
 * still have to measure load, store the devices, sort them, analyse them and
 * present the results.
 *
 * Build:  javac *.java
 * Run:    java demo [--report <file>] (run it from the folder containing ids)
 */
public class demo {
    public static void main(String[] args) {
        try {
            textInterfaceCli.displayHeader();
            System.out.println("Sampling system metrics... please wait 1 second...");

            cpuLoadCalculator.calculateLoads();

            textInterfaceCli.printCpuReport();


        } catch (InterruptedException e) {
            System.err.println("Error: System sampling execution was interrupted.");
        } catch (Exception e) {
            System.err.println("Fatal execution error: " + e.getMessage());
        }
    }
}

/*
public class demo
{
    public static void main(String[] args) throws Exception
    {
        if (args.length == 1 && args[0].equals("--gui")) {
            hardwareGui.launch();
            return;
        }
        Options options = parseOptions(args);
        StringWriter report = new StringWriter();
        try (PrintWriter out = new PrintWriter(report)) {
            writeReport(out, options);
        }

        if (options.reportPath == null) {
            System.out.print(report);
        } else {
            Path absolutePath = options.reportPath.toAbsolutePath();
            Path parent = absolutePath.getParent();
            if (parent != null)
                Files.createDirectories(parent);
            Files.writeString(absolutePath, report.toString(), StandardCharsets.UTF_8);
            System.out.println("Text report written to " + absolutePath);
        }
    }

    private static Options parseOptions(String[] args)
    {
        Options options = new Options();
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (i + 1 >= args.length)
                throw new IllegalArgumentException(usage());
            String value = args[++i];
            switch (argument) {
                case "--report":
                    if (value.isBlank())
                        throw new IllegalArgumentException("--report requires a file path");
                    options.reportPath = Path.of(value);
                    break;
                case "--vendor":
                    if (value.isBlank())
                        throw new IllegalArgumentException("--vendor requires a non-empty name");
                    options.vendorFilter = value;
                    break;
                case "--class":
                    if (value.isBlank())
                        throw new IllegalArgumentException("--class requires a class name or code");
                    options.classFilter = value;
                    break;
                case "--bus":
                    try {
                        options.busFilter = Integer.valueOf(value);
                        if (options.busFilter < 0)
                            throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("--bus must be a non-negative decimal number");
                    }
                    break;
                case "--type":
                    if (value.equalsIgnoreCase("pci"))
                        options.type = DeviceType.PCI;
                    else if (value.equalsIgnoreCase("usb"))
                        options.type = DeviceType.USB;
                    else if (value.equalsIgnoreCase("all"))
                        options.type = DeviceType.ALL;
                    else
                        throw new IllegalArgumentException("--type must be all, pci, or usb");
                    break;
                default:
                    throw new IllegalArgumentException(usage());
            }
        }
        if (options.classFilter != null && options.type == DeviceType.USB)
            throw new IllegalArgumentException("--class can only be used with PCI devices");
        return options;
    }

    private static String usage()
    {
        return "Usage: java -cp out demo [--report <file>] [--vendor <name>] "
             + "[--class <name-or-code>] [--bus <number>] [--type all|pci|usb] "
             + "or java -cp out demo --gui";
    }

    private static void writeReport(PrintWriter out, Options options) throws IOException
    {
        out.println("HARDWARE INVENTORY REPORT");
        out.println("Generated: " + LocalDateTime.now());
        String reportedType = options.classFilter == null
            ? options.type.name().toLowerCase(Locale.ROOT) : "pci";
        out.println("Device type: " + reportedType);
        if (options.vendorFilter != null)
            out.println("Vendor filter: " + options.vendorFilter);
        if (options.classFilter != null)
            out.println("PCI class filter: " + options.classFilter);
        if (options.busFilter != null)
            out.println("Bus filter: " + options.busFilter);
        out.println();

        boolean windows = System.getProperty("os.name").startsWith("Windows");
        boolean macos = System.getProperty("os.name").startsWith("Mac");
        if (windows || macos) {
            out.println("CPU and memory information require Linux /proc files.");
            out.println("USB bus and device numbers are only available on Linux.");
        } else {
            out.println("cores:  " + cpuInfo.getCount());
            out.println("model:  " + cpuInfo.getModel());
            out.println("core 0: busy=" + cpuInfo.getBusy(0) + " idle=" + cpuInfo.getIdle(0));
            out.println("memory: total=" + memInfo.getTotal() + " KiB, used=" + memInfo.getUsed() + " KiB");
        }

        idLookup pci = options.type == DeviceType.USB ? null : idLookup.pci();
        List<PciDevice> pciDevices = new ArrayList<>();
        if (options.type != DeviceType.USB) {
            for (int i = 0; i < pciInfo.getCount(); i++) {
                PciDevice device = new PciDevice(
                    pciInfo.getBus(i), pciInfo.getDevice(i), pciInfo.getFunction(i),
                    pciInfo.getVendorId(i), pciInfo.getDeviceId(i), pciInfo.getClassCode(i));
                if (matchesVendor(options.vendorFilter, device.vendorId,
                                  pci.vendorName(device.vendorId))
                    && matchesBus(options.busFilter, device.bus)
                    && matchesClass(options.classFilter, device.classCode,
                                    pci.className(device.classCode)))
                    pciDevices.add(device);
            }
        }

        if (options.type != DeviceType.USB) {
            Map<PciGroup, List<PciDevice>> groupedDevices = new EnumMap<>(PciGroup.class);
            for (PciGroup group : PciGroup.values())
                groupedDevices.put(group, new ArrayList<>());
            Map<String, List<PciDevice>> otherClasses = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (PciDevice device : pciDevices) {
                PciGroup group = pciGroup(device.classCode);
                if (group == PciGroup.OTHER) {
                    otherClasses.computeIfAbsent(pciClassKey(device.classCode, pci), key -> new ArrayList<>())
                                .add(device);
                } else {
                    groupedDevices.get(group).add(device);
                }
            }

            for (PciGroup group : PciGroup.values()) {
                if (group == PciGroup.OTHER)
                    continue;
                List<PciDevice> devices = groupedDevices.get(group);
                sortByVendor(devices, pci);
                printPciDevices(out, group.label, devices, pci);
            }

            out.println();
            out.println("OTHER PCI DEVICES GROUPED BY CLASS");
            if (otherClasses.isEmpty()) {
                out.println("  None");
            } else {
                for (Map.Entry<String, List<PciDevice>> entry : otherClasses.entrySet()) {
                    sortByVendor(entry.getValue(), pci);
                    printPciDevices(out, entry.getKey(), entry.getValue(), pci);
                }
            }

            Map<String, Integer> devicesPerClass = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (PciDevice device : pciDevices)
                devicesPerClass.merge(pciClassKey(device.classCode, pci), 1, Integer::sum);
            out.println();
            out.println("PCI DEVICE TOTALS BY CLASS");
            for (Map.Entry<String, Integer> entry : devicesPerClass.entrySet())
                out.printf("  %-48s %d%n", entry.getKey(), entry.getValue());
            out.println("Total PCI devices: " + pciDevices.size());

            Map<String, Integer> devicesPerVendor = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (PciDevice device : pciDevices) {
                String vendor = pciNameOrId(device.vendorId, pci.vendorName(device.vendorId));
                devicesPerVendor.merge(vendor, 1, Integer::sum);
            }
            out.println();
            out.println("PCI DEVICE COUNT BY VENDOR");
            for (Map.Entry<String, Integer> entry : devicesPerVendor.entrySet())
                out.printf("  %-40s %d%n", entry.getKey(), entry.getValue());
            out.println("Total PCI devices: " + pciDevices.size());
        }

        List<UsbDevice> usbDevices = new ArrayList<>();
        if (options.type != DeviceType.PCI && options.classFilter == null) {
            for (int i = 0; i < usbInfo.getCount(); i++) {
                UsbDevice device = new UsbDevice(usbInfo.getBusNumber(i), usbInfo.getDeviceNumber(i),
                                                 usbInfo.getVendorId(i), usbInfo.getProductId(i));
                if (matchesBus(options.busFilter, device.bus))
                    usbDevices.add(device);
            }
        }

        if (options.type != DeviceType.PCI && options.classFilter == null) {
            idLookup usb = usbDevices.isEmpty() ? null : idLookup.usb();
            if (usb != null) {
                usbDevices.removeIf(device ->
                    !matchesVendor(options.vendorFilter, device.vendorId,
                                   usb.vendorName(device.vendorId)));
                usbDevices.sort(Comparator
                    .comparing((UsbDevice device) -> pciNameOrId(
                        device.vendorId, usb.vendorName(device.vendorId)),
                        String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(device -> device.vendorId)
                    .thenComparingInt(device -> device.productId));
            }
            out.println("USB devices: " + usbDevices.size());
            if (usbDevices.isEmpty()) {
                out.println("No USB devices match the selected filters.");
            } else {
                for (int i = 0; i < usbDevices.size(); i++) {
                    UsbDevice device = usbDevices.get(i);
                    out.printf("  %d. Bus %s Device %s: ID %s:%s  %s %s%n",
                               i + 1, paddedNumberOrUnknown(device.bus),
                               paddedNumberOrUnknown(device.device),
                               hexId(device.vendorId), hexId(device.productId),
                               pciNameOrId(device.vendorId, usb.vendorName(device.vendorId)),
                               pciNameOrId(device.productId,
                                           usb.deviceName(device.vendorId, device.productId)));
                }
            }
        }
    }

    private static boolean matchesVendor(String filter, int vendorId, String vendorName)
    {
        if (filter == null)
            return true;
        String value = pciNameOrId(vendorId, vendorName).toLowerCase(Locale.ROOT);
        return value.contains(filter.toLowerCase(Locale.ROOT));
    }

    private static boolean matchesBus(Integer filter, int bus)
    {
        return filter == null || bus == filter;
    }

    private static boolean matchesClass(String filter, int classCode, String className)
    {
        if (filter == null)
            return true;
        String normalized = filter.toLowerCase(Locale.ROOT);
        if (className != null && className.toLowerCase(Locale.ROOT).contains(normalized))
            return true;
        String code = hexClass(classCode).toLowerCase(Locale.ROOT);
        String codeFilter = normalized.startsWith("0x") ? normalized.substring(2) : normalized;
        return classCode >= 0 && code.startsWith(codeFilter);
    }

    private static void sortByVendor(List<PciDevice> devices, idLookup pci)
    {
        devices.sort(Comparator
            .comparing((PciDevice device) -> pciNameOrId(
                device.vendorId, pci.vendorName(device.vendorId)), String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(device -> device.vendorId)
            .thenComparingInt(device -> device.deviceId)
            .thenComparingInt(device -> sortNumber(device.bus))
            .thenComparingInt(device -> sortNumber(device.device))
            .thenComparingInt(device -> sortNumber(device.function)));
    }

    private static PciGroup pciGroup(int classCode)
    {
        if (classCode < 0)
            return PciGroup.OTHER;
        int baseClass = (classCode >>> 16) & 0xff;
        int subClass = (classCode >>> 8) & 0xff;
        switch (baseClass) {
            case 0x01:
                return PciGroup.STORAGE;
            case 0x02:
                return PciGroup.NETWORK;
            case 0x03:
                return PciGroup.DISPLAY;
            case 0x06:
                return PciGroup.BRIDGE;
            case 0x0c:
                return subClass == 0x03 ? PciGroup.USB_CONTROLLER : PciGroup.OTHER;
            default:
                return PciGroup.OTHER;
        }
    }

    private static String pciClassKey(int classCode, idLookup pci)
    {
        if (classCode < 0)
            return "unknown class";
        int classAndSubClass = (classCode >>> 8) & 0xffff;
        return String.format("%04x %s", classAndSubClass,
                             nameOrUnknown(pci.className(classCode)));
    }

    private static void printPciDevices(PrintWriter out, String heading, List<PciDevice> devices,
                                        idLookup pci)
    {
        out.println();
        out.println(heading + " (" + devices.size() + ")");
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
        out.printf(columns, "No.", "Bus", "Device", "Function", "Vendor", "Device",
                   "Class code", "Class");
        out.printf(columns, "---", "---", "------", "--------", "------", "------",
                   "----------", "-----");
        for (int i = 0; i < devices.size(); i++) {
            PciDevice device = devices.get(i);
            String vendor = pciNameOrId(device.vendorId, pci.vendorName(device.vendorId));
            String description = pciNameOrId(device.deviceId,
                                             pci.deviceName(device.vendorId, device.deviceId));
            out.printf(columns,
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

    private static String paddedNumberOrUnknown(int number)
    {
        return number < 0 ? "unknown" : String.format("%03d", number);
    }

    private static int sortNumber(int number)
    {
        return number < 0 ? Integer.MAX_VALUE : number;
    }

    private static String nameOrUnknown(String name)
    {
        return name == null ? "unknown" : name;
    }

    private enum DeviceType
    {
        ALL,
        PCI,
        USB
    }

    private enum PciGroup
    {
        NETWORK("NETWORK CONTROLLERS"),
        DISPLAY("DISPLAY CONTROLLERS"),
        STORAGE("STORAGE CONTROLLERS"),
        BRIDGE("BRIDGES"),
        USB_CONTROLLER("USB CONTROLLERS"),
        OTHER("OTHER PCI DEVICES");

        final String label;

        PciGroup(String label)
        {
            this.label = label;
        }
    }

    private static final class Options
    {
        Path reportPath;
        String vendorFilter;
        String classFilter;
        Integer busFilter;
        DeviceType type = DeviceType.ALL;
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

    private static final class UsbDevice
    {
        final int bus;
        final int device;
        final int vendorId;
        final int productId;

        UsbDevice(int bus, int device, int vendorId, int productId)
        {
            this.bus = bus;
            this.device = device;
            this.vendorId = vendorId;
            this.productId = productId;
        }
    }
}
*/
