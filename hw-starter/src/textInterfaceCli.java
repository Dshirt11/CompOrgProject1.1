import java.io.PrintWriter;

/** Text-formatting helpers shared by the console and saved CLI report. */
public class textInterfaceCli {

    /** Print the report header directly to the console. */
    public static void displayHeader() {
        displayHeader(new PrintWriter(System.out, true));
    }

    /** Print the header to the supplied destination. */
    public static void displayHeader(PrintWriter out) {
        out.println("==================================================");
        out.println("            SYSTEM INSPECTION REPORT              ");
        out.println("==================================================");
    }

    /** Print the CPU summary directly to the console. */
    public static void printCpuReport() {
        printCpuReport(new PrintWriter(System.out, true));
    }

    /**
     * Print sampled CPU information.
     * calculateLoads() must run first so the load values have been populated.
     */
    public static void printCpuReport(PrintWriter out) {
        out.printf("CPU Model            : %s%n", cpuInfo.getModel());
        out.printf("Total Hardware Cores : %d%n", cpuInfo.getCount());
        out.printf("Average System Load  : %.2f%%%n", cpuLoadCalculator.averageSystemLoad);
        out.println("--------------------------------------------------");
        out.println(" Per-Core Load Breakdown:");

        // The array index is the logical processor number used by cpuInfo.
        for (int i = 0; i < cpuLoadCalculator.perCoreLoads.length; i++) {
            double load = cpuLoadCalculator.perCoreLoads[i];
            out.printf("  --- Core %d :  %.2f%%%n", i, load);
        }
        out.println("==================================================");
    }

}
