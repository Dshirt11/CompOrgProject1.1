/**
 * Samples CPU counters twice and calculates processor load percentages.
 *
 * Linux counters are cumulative since boot, so comparing two samples gives
 * the amount of time spent working and idle during the sampling interval.
 */
public class cpuLoadCalculator {

    /** Load percentage for each logical processor, indexed by core number. */
	public static double[] perCoreLoads;

    /** Load percentage across all logical processors for the same interval. */
	public static double averageSystemLoad;

    /**
     * Read counters, wait one second, and calculate per-core and overall load.
     * The results are stored in {@link #perCoreLoads} and
     * {@link #averageSystemLoad}.
     */
   	public static void calculateLoads() throws InterruptedException {
        // Find how many processor entries to sample and allocate a slot for
        // each core's first busy and idle counter values.
        int numCores = cpuInfo.getCount();
        long[] busyTime1 = new long[numCores];
        long[] idleTime1 = new long[numCores];

        // Take the first sample before the delay.
        for (int i = 0; i < numCores; i++) {
            busyTime1[i] = cpuInfo.getBusy(i);
            idleTime1[i] = cpuInfo.getIdle(i);
        }

        // Give the counters time to advance so the difference measures load.
        Thread.sleep(1000);

        // Allocate the output array and totals used for the overall percentage.
        perCoreLoads = new double[numCores];
        long totalSystemBusyDelta = 0;
        long totalSystemIdleDelta = 0;

        // Compare each second sample with the first. Busy / total elapsed ticks
        // is the fraction of time this core was not idle.
	    for (int i = 0; i < numCores; i++) {
            long busyDelta = cpuInfo.getBusy(i) - busyTime1[i];
            long idleDelta = cpuInfo.getIdle(i) - idleTime1[i];
            long totalDelta = busyDelta + idleDelta;

            // Avoid dividing by zero if the counters did not change.
            if (totalDelta == 0) {
                perCoreLoads[i] = 0.0;
            } else {
                perCoreLoads[i] = (busyDelta * 1.0 / totalDelta) * 100.0;
            }

            // Add each core's interval to the system-wide totals.
            totalSystemBusyDelta += busyDelta;
            totalSystemIdleDelta += idleDelta;
        }

        // Calculate the combined load from total busy and idle time across cores.
        long totalSystemTime = totalSystemBusyDelta + totalSystemIdleDelta;
        if (totalSystemTime == 0) {
            averageSystemLoad = 0.0;
        } else {
            averageSystemLoad = (totalSystemBusyDelta * 1.0 / totalSystemTime) * 100.0;
        }
    }
}
