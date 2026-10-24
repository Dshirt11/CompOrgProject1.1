public class cpuLoadCalculator {
	
	public static double[] perCoreLoads;
	public static double averageSystemLoad;

   	public static void calculateLoads() throws InterruptedException {
        int numCores = cpuInfo.getCount();

        long[] busyTime1 = new long[numCores];
        long[] idleTime1 = new long[numCores];

        for (int i = 0; i < numCores; i++) {
            busyTime1[i] = cpuInfo.getBusy(i);
            idleTime1[i] = cpuInfo.getIdle(i);
        }

        Thread.sleep(1000);

        perCoreLoads = new double[numCores];
        long totalSystemBusyDelta= 0;
        long totalSystemIdleDelta = 0;

       
       //calculation of difference btn busy time of cpu n and second busy time of cpu n 
       //same wise to the calculation of the idle time 
       //the formula for load is busydifference /totaldifference multiplied by 100;
	for (int i = 0; i < numCores; i++) {
            long busyDelta = cpuInfo.getBusy(i) - busyTime1[i];
            long idleDelta = cpuInfo.getIdle(i) - idleTime1[i];
            long totalDelta = busyDelta + idleDelta;

            if (totalDelta == 0) {
                perCoreLoads[i] = 0.0;
            } else {
                perCoreLoads[i] = ( busyDelta * 1.0 / totalDelta) * 100.0;
            }
            totalSystemBusyDelta += busyDelta;
            totalSystemIdleDelta += idleDelta;
        }

        long totalSystemTime = totalSystemBusyDelta + totalSystemIdleDelta;
        if (totalSystemTime == 0) {
            averageSystemLoad = 0.0;
        } else {
            averageSystemLoad = ( totalSystemBusyDelta*1.0 / totalSystemTime) * 100.0;
        }
    }
}

