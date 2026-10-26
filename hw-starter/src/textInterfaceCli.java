public class textInterfaceCli {

    public static void displayHeader() {
        System.out.println("==================================================");
        System.out.println("            SYSTEM INSPECTION REPORT              ");
        System.out.println("==================================================");
    }

    public static void printCpuReport() {
        System.out.printf("CPU Model            : %s\n", cpuInfo.getModel());
        System.out.printf("Total Hardware Cores : %d\n", cpuInfo.getCount());
        System.out.printf("Average System Load  : %.2f%%\n", cpuLoadCalculator.averageSystemLoad);
        System.out.println("--------------------------------------------------");
        System.out.println(" Per-Core Load Breakdown:");
        
        for (int i = 0; i < cpuLoadCalculator.perCoreLoads.length; i++) {
            double load = cpuLoadCalculator.perCoreLoads[i];
            System.out.printf("  --- Core %d :  %.2f%%\n", i, load);
        }
        System.out.println("==================================================");
    }

}

