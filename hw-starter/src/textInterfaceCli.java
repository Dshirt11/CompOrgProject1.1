import java.io.PrintWriter;

public class textInterfaceCli {

    public static void displayHeader() {
        displayHeader(new PrintWriter(System.out, true));
    }

    public static void displayHeader(PrintWriter out) {
        out.println("==================================================");
        out.println("            SYSTEM INSPECTION REPORT              ");
        out.println("==================================================");
    }

    public static void printCpuReport() {
        printCpuReport(new PrintWriter(System.out, true));
    }

    public static void printCpuReport(PrintWriter out) {
        out.printf("CPU Model            : %s%n", cpuInfo.getModel());
        out.printf("Total Hardware Cores : %d%n", cpuInfo.getCount());
        out.printf("Average System Load  : %.2f%%%n", cpuLoadCalculator.averageSystemLoad);
        out.println("--------------------------------------------------");
        out.println(" Per-Core Load Breakdown:");

        for (int i = 0; i < cpuLoadCalculator.perCoreLoads.length; i++) {
            double load = cpuLoadCalculator.perCoreLoads[i];
            out.printf("  --- Core %d :  %.2f%%%n", i, load);
        }
        out.println("==================================================");
    }

}
