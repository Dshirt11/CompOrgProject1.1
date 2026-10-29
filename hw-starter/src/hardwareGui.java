import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/** Optional Swing interface for viewing and exporting hardware information. */
public class hardwareGui
{
    // Components are fields so loading, filtering, and exporting can update them.
    private final JFrame frame = new JFrame("Hardware Inspector");
    private final JLabel status = new JLabel("Loading hardware information...");
    private final JButton exportButton = new JButton("Export visible tables...");
    private final JTabbedPane tabs = new JTabbedPane();
    private final List<JTable> tables = new ArrayList<>();

    /** Open the hardware tables, or explain when no graphical display is available. */
    public static void launch()
    {
        // Swing windows cannot be created in a headless terminal environment.
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("Cannot open the GUI: no graphical display is available.");
            return;
        }
        javax.swing.SwingUtilities.invokeLater(() -> new hardwareGui().showWindow());
    }

    private void showWindow()
    {
        // Build the main window shell before starting the potentially slow reads.
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(760, 480));
        frame.setSize(1050, 650);
        frame.setLocationByPlatform(true);

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10));
        header.add(status, BorderLayout.CENTER);
        header.add(exportButton, BorderLayout.EAST);
        frame.add(header, BorderLayout.NORTH);
        frame.add(tabs, BorderLayout.CENTER);

        exportButton.setEnabled(false);
        exportButton.addActionListener(event -> exportVisibleTables());
        frame.setVisible(true);

        // Read hardware off the Swing event thread so the window remains responsive.
        new SwingWorker<HardwareData, Void>()
        {
            @Override
            protected HardwareData doInBackground() throws Exception
            {
                return loadHardwareData();
            }

            @Override
            protected void done()
            {
                try {
                    // SwingWorker.done() runs on the event thread, so it is safe
                    // to install the tables and update the status here.
                    addTabs(get());
                    status.setText("Hardware information loaded. Filters search all columns.");
                    exportButton.setEnabled(true);
                } catch (InterruptedException e) {
                    // Restore the interrupt signal after the worker wait is interrupted.
                    Thread.currentThread().interrupt();
                    status.setText("Could not load hardware information.");
                    JOptionPane.showMessageDialog(frame, e.getMessage(),
                        "Hardware query failed", JOptionPane.ERROR_MESSAGE);
                } catch (ExecutionException e) {
                    // Show the underlying hardware-read error rather than a wrapper.
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    status.setText("Could not load hardware information.");
                    JOptionPane.showMessageDialog(frame, cause.getMessage(),
                        "Hardware query failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static HardwareData loadHardwareData() throws IOException
    {
        // Collect the data into table rows first; Swing components are built later.
        HardwareData data = new HardwareData();

        // CPU and memory rows use the same labels/values format.
        List<Object[]> cpuRows = new ArrayList<>();
        cpuRows.add(new Object[] {"Processor model", cpuInfo.getModel()});
        cpuRows.add(new Object[] {"Logical processors", cpuInfo.getCount()});
        for (int core = 0; core < cpuInfo.getCount(); core++) {
            cpuRows.add(new Object[] {"Core " + core + " busy ticks", cpuInfo.getBusy(core)});
            cpuRows.add(new Object[] {"Core " + core + " idle ticks", cpuInfo.getIdle(core)});
        }
        data.cpuRows = cpuRows.toArray(new Object[0][]);

        data.memoryRows = new Object[][] {
            {"Total memory (KiB)", displayValue(memInfo.getTotal())},
            {"Available memory (KiB)", displayValue(memInfo.getAvailable())},
            {"Used memory (KiB)", displayValue(memInfo.getUsed())},
            {"Used memory (%)", displayPercentage(memInfo.getUsedPercentage())}
        };

        // Look up readable names once, then reuse them for every PCI row.
        int pciCount = pciInfo.getCount();
        idLookup pciNames = pciCount == 0 ? null : idLookup.pci();
        List<Object[]> pciRows = new ArrayList<>();
        for (int i = 0; i < pciCount; i++) {
            int vendorId = pciInfo.getVendorId(i);
            int deviceId = pciInfo.getDeviceId(i);
            int classCode = pciInfo.getClassCode(i);
            pciRows.add(new Object[] {
                pciInfo.getAddress(i),
                displayNumber(pciInfo.getBus(i)),
                displayNumber(pciInfo.getDevice(i)),
                displayNumber(pciInfo.getFunction(i)),
                hexId(vendorId),
                lookupName(pciNames.vendorName(vendorId), vendorId),
                hexId(deviceId),
                lookupName(pciNames.deviceName(vendorId, deviceId), deviceId),
                hexClass(classCode),
                pciNames.className(classCode) == null ? "unknown" : pciNames.className(classCode)
            });
        }
        data.pciRows = pciRows.toArray(new Object[0][]);

        // USB name tables are also loaded once and reused for all USB rows.
        int usbCount = usbInfo.getCount();
        idLookup usbNames = usbCount == 0 ? null : idLookup.usb();
        List<Object[]> usbRows = new ArrayList<>();
        for (int i = 0; i < usbCount; i++) {
            int vendorId = usbInfo.getVendorId(i);
            int productId = usbInfo.getProductId(i);
            String vendor = usbNames.vendorName(vendorId);
            String product = usbNames.deviceName(vendorId, productId);
            usbRows.add(new Object[] {
                usbInfo.getName(i),
                displayNumber(usbInfo.getBusNumber(i)),
                displayNumber(usbInfo.getDeviceNumber(i)),
                hexId(vendorId),
                vendor == null ? usbInfo.getManufacturer(i) : vendor,
                hexId(productId),
                product == null ? usbInfo.getProduct(i) : product
            });
        }
        data.usbRows = usbRows.toArray(new Object[0][]);
        return data;
    }

    private void addTabs(HardwareData data)
    {
        // Keep one table per hardware category for independent sorting/filtering.
        addTableTab("CPU", new String[] {"Information", "Value"}, data.cpuRows);
        addTableTab("Memory", new String[] {"Information", "Value"}, data.memoryRows);
        addTableTab("PCI", new String[] {"Address", "Bus", "Device", "Function", "Vendor ID",
            "Vendor", "Device ID", "Device", "Class code", "Class"},
            data.pciRows);
        addTableTab("USB", new String[] {"Name", "Bus", "Device", "Vendor ID", "Vendor",
            "Product ID", "Product"}, data.usbRows);
    }

    private void addTableTab(String title, String[] columns, Object[][] rows)
    {
        // Disable editing: these tables display a hardware snapshot, not input forms.
        DefaultTableModel model = new DefaultTableModel(rows, columns)
        {
            @Override
            public boolean isCellEditable(int row, int column)
            {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        tables.add(table);

        // A separate sorter lets this tab filter its displayed rows as the user types.
        JTextField filter = new JTextField(24);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            private void updateFilter()
            {
                String value = filter.getText().trim();
                // Quote the user's text so punctuation is treated literally, not as regex.
                sorter.setRowFilter(value.isEmpty()
                    ? null : RowFilter.regexFilter("(?i)" + Pattern.quote(value)));
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event)
            {
                updateFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event)
            {
                updateFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event)
            {
                updateFilter();
            }
        });

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        filterPanel.add(new JLabel("Filter:"));
        filterPanel.add(filter);
        JPanel tab = new JPanel(new BorderLayout());
        tab.add(filterPanel, BorderLayout.NORTH);
        tab.add(new JScrollPane(table), BorderLayout.CENTER);
        tabs.addTab(title, tab);
    }

    private void exportVisibleTables()
    {
        // Ask where to save; cancelling the dialog leaves the current window unchanged.
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export visible hardware tables");
        chooser.setSelectedFile(new java.io.File("hardware-inventory.txt"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION)
            return;

        Path output = chooser.getSelectedFile().toPath();
        // Protect existing files from accidental replacement.
        if (Files.exists(output)
            && JOptionPane.showConfirmDialog(frame, "Replace " + output + "?",
                "Confirm overwrite", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;
        StringBuilder text = new StringBuilder("HARDWARE INVENTORY\nGenerated: ")
            .append(LocalDateTime.now()).append(System.lineSeparator());
        // Export the view order, which reflects both active filters and sorting.
        for (int tabIndex = 0; tabIndex < tabs.getTabCount(); tabIndex++)
            appendVisibleTable(text, tabs.getTitleAt(tabIndex), tables.get(tabIndex));

        try {
            Files.writeString(output, text.toString(), StandardCharsets.UTF_8);
            status.setText("Exported visible table rows to " + output.toAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Could not write report: " + e.getMessage(),
                "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    static void appendVisibleTable(StringBuilder text, String title, JTable table)
    {
        // JTable row/column coordinates can differ from model coordinates after
        // sorting, so convert each visible coordinate before reading cell values.
        text.append(System.lineSeparator()).append(title).append(System.lineSeparator());
        for (int column = 0; column < table.getColumnCount(); column++) {
            if (column > 0)
                text.append(" | ");
            int modelColumn = table.convertColumnIndexToModel(column);
            text.append(table.getModel().getColumnName(modelColumn));
        }
        text.append(System.lineSeparator());
        if (table.getRowCount() == 0)
            text.append("(No matching rows)").append(System.lineSeparator());
        for (int row = 0; row < table.getRowCount(); row++) {
            if (row > 0)
                text.append(System.lineSeparator());
            int modelRow = table.convertRowIndexToModel(row);
            for (int column = 0; column < table.getColumnCount(); column++) {
                if (column > 0)
                    text.append(" | ");
                int modelColumn = table.convertColumnIndexToModel(column);
                Object value = table.getModel().getValueAt(modelRow, modelColumn);
                text.append(value == null ? "" : value.toString().replaceAll("[\\r\\n]+", " "));
            }
        }
        text.append(System.lineSeparator());
    }

    private static String displayValue(long value)
    {
        // Negative values are the reader classes' marker for unavailable data.
        return value < 0 ? "unavailable" : Long.toString(value);
    }

    private static String displayPercentage(double value)
    {
        return value < 0 ? "unavailable" : String.format(Locale.ROOT, "%.2f%%", value);
    }

    private static String displayNumber(int value)
    {
        return value < 0 ? "unavailable" : Integer.toString(value);
    }

    private static String hexId(int value)
    {
        // Vendor and product IDs are 16-bit values.
        return value < 0 ? "unknown" : String.format("%04x", value);
    }

    private static String hexClass(int value)
    {
        return value < 0 ? "unknown" : String.format("%06x", value);
    }

    private static String lookupName(String name, int id)
    {
        // Keep the numeric identifier visible when the local database lacks a name.
        if (name != null)
            return name;
        return id < 0 ? "unknown" : String.format("0x%04x", id);
    }

    private static final class HardwareData
    {
        // Object[][] matches the row/column shape expected by DefaultTableModel.
        Object[][] cpuRows;
        Object[][] memoryRows;
        Object[][] pciRows;
        Object[][] usbRows;
    }
}
