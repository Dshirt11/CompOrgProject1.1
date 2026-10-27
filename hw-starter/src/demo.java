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
 * Application entry point for the hardware inventory.
 *
 * It reads command-line options, gathers Linux hardware data, applies any
 * requested filters, and prints the resulting report or saves it to a file.
 * Run from the project directory so the {@code ids} folder can be found.
 */
public class demo
{
    public static void main(String[] args) throws Exception
    {
        // The GUI is a separate mode and does not use the CLI report pipeline.
        if (args.length == 1 && args[0].equals("--gui")) {
            hardwareGui.launch();
            return;
        }

        // Convert arguments into a single options object, then build the report
        // once so terminal output and saved output contain the same information.
        Options options = parseOptions(args);
        StringWriter report = new StringWriter();
        try (PrintWriter out = new PrintWriter(report)) {
            writeReport(out, options);
        }

        if (options.reportPath == null) {
            // No destination was requested, so write the generated text to stdout.
            System.out.print(report);
        } else {
            // Resolve the output path and create any missing parent directories.
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
            // Every supported option takes one value; a missing value is a usage error.
            if (i + 1 >= args.length)
                throw new IllegalArgumentException(usage());
            String value = args[++i];
            switch (argument) {
                case "--report":
                    // Store the report destination for main() to write after generation.
                    if (value.isBlank())
                        throw new IllegalArgumentException("--report requires a file path");
                    options.reportPath = Path.of(value);
                    break;
                case "--vendor":
                    // Vendor text is matched against the name, or the ID if unknown.
                    if (value.isBlank())
                        throw new IllegalArgumentException("--vendor requires a non-empty name");
                    options.vendorFilter = value;
                    break;
                case "--class":
                    // PCI class names and codes are handled by matchesClass().
                    if (value.isBlank())
                        throw new IllegalArgumentException("--class requires a class name or code");
                    options.classFilter = value;
                    break;
                case "--bus":
                    // Parse as decimal and reject negative or malformed bus numbers.
                    try {
                        options.busFilter = Integer.valueOf(value);
                        if (options.busFilter < 0)
                            throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("--bus must be a non-negative decimal number");
                    }
                    break;
                case "--type":
                    // Select whether PCI, USB, or both device sections are generated.
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
        // A PCI class cannot select any USB devices, so reject this combination early.
        if (options.classFilter != null && options.type == DeviceType.USB)
            throw new IllegalArgumentException("--class can only be used with PCI devices");
        return options;
    }

    private static String usage()
    {
        // Keep the accepted syntax in one place for all argument errors.
        return "Usage: java -cp out demo [--report <file>] [--vendor <name>] "
             + "[--class <name-or-code>] [--bus <number>] [--type all|pci|usb] "
             + "or java -cp out demo --gui";
    }

    private static void writeReport(PrintWriter out, Options options)
        throws IOException, InterruptedException
    {
        // Write the common heading and echo active filters so the report is self-describing.
        textInterfaceCli.displayHeader(out);
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

        // CPU load needs two Linux counter samples separated by one second.
        cpuLoadCalculator.calculateLoads();
        textInterfaceCli.printCpuReport(out);
        double usedPercentage = memInfo.getUsedPercentage();
        String percentage = usedPercentage < 0
            ? "unavailable" : String.format(Locale.ROOT, "%.2f%%", usedPercentage);
        out.println("memory: total=" + memInfo.getTotal() + " KiB, used="
                    + memInfo.getUsed() + " KiB (" + percentage + ")");

        // Load only the name database needed for the selected device type.
        idLookup pci = options.type == DeviceType.USB ? null : idLookup.pci();
        List<PciDevice> pciDevices = new ArrayList<>();
        if (options.type != DeviceType.USB) {
            // Snapshot each PCI device and keep it only if all active filters match.
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
            // Prepare one list per special category and a name-keyed map for other classes.
            Map<PciGroup, List<PciDevice>> groupedDevices = new EnumMap<>(PciGroup.class);
            for (PciGroup group : PciGroup.values())
                groupedDevices.put(group, new ArrayList<>());
            Map<String, List<PciDevice>> otherClasses = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (PciDevice device : pciDevices) {
                // Special controller types get their own sections; all others are
                // grouped by their PCI base/subclass code.
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
                // Sort each category before printing its table.
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

            // Count only devices that survived filtering.
            Map<String, Integer> devicesPerClass = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (PciDevice device : pciDevices)
                devicesPerClass.merge(pciClassKey(device.classCode, pci), 1, Integer::sum);
            out.println();
            out.println("PCI DEVICE TOTALS BY CLASS");
            for (Map.Entry<String, Integer> entry : devicesPerClass.entrySet())
                out.printf("  %-48s %d%n", entry.getKey(), entry.getValue());
            out.println("Total PCI devices: " + pciDevices.size());

            // Build a second summary, this time keyed by vendor name.
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
            // Class filters are PCI-only; bus filtering applies to USB where available.
            for (int i = 0; i < usbInfo.getCount(); i++) {
                UsbDevice device = new UsbDevice(usbInfo.getBusNumber(i), usbInfo.getDeviceNumber(i),
                                                 usbInfo.getVendorId(i), usbInfo.getProductId(i));
                if (matchesBus(options.busFilter, device.bus))
                    usbDevices.add(device);
            }
        }

        if (options.type != DeviceType.PCI && options.classFilter == null) {
            // Avoid loading the USB name database if no devices remain to display.
            idLookup usb = usbDevices.isEmpty() ? null : idLookup.usb();
            if (usb != null) {
                // Apply the shared vendor filter and sort by readable vendor name.
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

    /** Return whether a vendor filter matches the known name or ID fallback. */
    private static boolean matchesVendor(String filter, int vendorId, String vendorName)
    {
        if (filter == null)
            return true;
        // Match case-insensitively against the vendor name or a hexadecimal fallback.
        String value = pciNameOrId(vendorId, vendorName).toLowerCase(Locale.ROOT);
        return value.contains(filter.toLowerCase(Locale.ROOT));
    }

    /** Bus-filter comparison shared by PCI and USB devices. */
    private static boolean matchesBus(Integer filter, int bus)
    {
        // Missing bus numbers do not match an explicitly requested bus.
        return filter == null || bus == filter;
    }

    /** Match a PCI class by part of its name or by a hexadecimal code prefix. */
    private static boolean matchesClass(String filter, int classCode, String className)
    {
        if (filter == null)
            return true;
        // Accept a partial class description or a prefix of its hexadecimal code.
        String normalized = filter.toLowerCase(Locale.ROOT);
        if (className != null && className.toLowerCase(Locale.ROOT).contains(normalized))
            return true;
        String code = hexClass(classCode).toLowerCase(Locale.ROOT);
        String codeFilter = normalized.startsWith("0x") ? normalized.substring(2) : normalized;
        return classCode >= 0 && code.startsWith(codeFilter);
    }

    /** Sort a PCI category by vendor name and then by IDs and hardware address. */
    private static void sortByVendor(List<PciDevice> devices, idLookup pci)
    {
        // Use stable tie-breakers so same-vendor devices have deterministic ordering.
        devices.sort(Comparator
            .comparing((PciDevice device) -> pciNameOrId(
                device.vendorId, pci.vendorName(device.vendorId)), String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(device -> device.vendorId)
            .thenComparingInt(device -> device.deviceId)
            .thenComparingInt(device -> sortNumber(device.bus))
            .thenComparingInt(device -> sortNumber(device.device))
            .thenComparingInt(device -> sortNumber(device.function)));
    }

    /** Map the PCI base/subclass code to a named report category. */
    private static PciGroup pciGroup(int classCode)
    {
        if (classCode < 0)
            return PciGroup.OTHER;
        // PCI class codes encode base class in the upper byte and subclass below it.
        int baseClass = (classCode >>> 16) & 0xff;
        int subClass = (classCode >>> 8) & 0xff;
        // These are the controller categories highlighted in the inventory.
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

    /** Create the label used to group PCI devices outside the named categories. */
    private static String pciClassKey(int classCode, idLookup pci)
    {
        if (classCode < 0)
            return "unknown class";
        // Group by base/subclass, excluding the programming-interface byte.
        int classAndSubClass = (classCode >>> 8) & 0xffff;
        return String.format("%04x %s", classAndSubClass,
                             nameOrUnknown(pci.className(classCode)));
    }

    /** Print a heading and aligned rows for one PCI group. */
    private static void printPciDevices(PrintWriter out, String heading, List<PciDevice> devices,
                                        idLookup pci)
    {
        out.println();
        out.println(heading + " (" + devices.size() + ")");
        // Measure the longest names first so the printed columns align to content.
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
        // Print the header, separator, and then one row per device.
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

    /** Format a vendor or device ID, or report that it is unavailable. */
    private static String hexId(int id)
    {
        // IDs are conventionally shown as four lowercase hexadecimal digits.
        return id < 0 ? "unknown" : String.format("%04x", id);
    }

    /** Format a 24-bit PCI class code as six hexadecimal digits. */
    private static String hexClass(int classCode)
    {
        return classCode < 0 ? "unknown" : String.format("%06x", classCode);
    }

    /** Use a database name when known, otherwise show the ID. */
    private static String pciNameOrId(int id, String name)
    {
        // Prefer a database name; fall back to the raw ID if it is not listed.
        if (name != null)
            return name;
        return id < 0 ? "unknown" : String.format("0x%04x", id);
    }

    /** Format a location number without padding for the PCI table. */
    private static String numberOrUnknown(int number)
    {
        return number < 0 ? "unknown" : Integer.toString(number);
    }

    /** Format a bus/device number to match the conventional three-digit USB display. */
    private static String paddedNumberOrUnknown(int number)
    {
        return number < 0 ? "unknown" : String.format("%03d", number);
    }

    /** Convert unavailable numbers to a value that sorts after valid numbers. */
    private static int sortNumber(int number)
    {
        // Treat unavailable numbers as larger than any normal bus/device number.
        return number < 0 ? Integer.MAX_VALUE : number;
    }

    /** Replace a missing database label with readable fallback text. */
    private static String nameOrUnknown(String name)
    {
        return name == null ? "unknown" : name;
    }

    /** Which device sections the user requested in the report. */
    private enum DeviceType
    {
        ALL,
        PCI,
        USB
    }

    /** The named PCI categories printed before the catch-all class groups. */
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
        // Null means the corresponding filter or output path was not supplied.
        Path reportPath;
        String vendorFilter;
        String classFilter;
        Integer busFilter;
        DeviceType type = DeviceType.ALL;
    }

    private static final class PciDevice
    {
        // Capture the values used by filtering, sorting, grouping, and rendering.
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
        // USB exposes bus/device numbers and vendor/product IDs for the report.
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
