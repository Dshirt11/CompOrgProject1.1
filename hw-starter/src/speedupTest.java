
public class speedupTest
{
    // Count primes below this number. Make it bigger if the 1 thread run is too quick.
    private static final int LIMIT = 8000000;

    // The numbers of threads to try.
    private static final int[] THREAD_COUNTS = {1, 2, 4, 8};

    // Each run is repeated and the fastest time is kept, so one unlucky run
    // (for example the computer doing something else) does not spoil the result.
    private static final int REPEATS = 3;

    // The numbers are handed out in blocks of this size, like dealing cards.
    private static final int BLOCK = 1000;

    private static class PrimeWorker extends Thread
    {
        private final int id;
        private final int threads;
        private final int limit;
        private int found = 0;

        PrimeWorker(int id, int threads, int limit)
        {
            this.id = id;
            this.threads = threads;
            this.limit = limit;
        }

        @Override
        public void run()
        {
            // Jump from one of our blocks to the next one.
            for (int blockStart = id * BLOCK; blockStart < limit; blockStart = blockStart + threads * BLOCK) {
                int blockEnd = Math.min(blockStart + BLOCK, limit);
                for (int n = blockStart; n < blockEnd; n++) {
                    if (isPrime(n))
                        found++;
                }
            }
        }

        int getFound()
        {
            return found;
        }
    }

    // True if n is a prime number. Tries to divide n by the odd numbers up to
    // the square root of n.
    private static boolean isPrime(int n)
    {
        if (n < 2)
            return false;
        if (n < 4)
            return true;
        if (n % 2 == 0)
            return false;
        for (int d = 3; (long) d * d <= n; d = d + 2) {
            if (n % d == 0)
                return false;
        }
        return true;
    }

    // Counts the primes below 'limit' using the given number of threads.
    private static int countPrimes(int threads, int limit) throws InterruptedException
    {
        // Create and start all the workers.
        PrimeWorker[] workers = new PrimeWorker[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new PrimeWorker(i, threads, limit);
            workers[i].start();
        }

        // Wait for every worker to finish, then add up what each one found.
        int total = 0;
        for (int i = 0; i < threads; i++) {
            workers[i].join();
            total = total + workers[i].getFound();
        }
        return total;
    }

    // The busy ticks of every core right now, read with cpuInfo.
    private static long[] readBusy(int cores)
    {
        long[] busy = new long[cores];
        for (int i = 0; i < cores; i++)
            busy[i] = cpuInfo.getBusy(i);
        return busy;
    }

    // The idle ticks of every core right now, read with cpuInfo.
    private static long[] readIdle(int cores)
    {
        long[] idle = new long[cores];
        for (int i = 0; i < cores; i++)
            idle[i] = cpuInfo.getIdle(i);
        return idle;
    }

    // Amdahl's law: the speedup you get with n threads if a fraction s of the
    // program can not be made parallel.
    private static double amdahl(double s, int n)
    {
        return 1.0 / (s + (1.0 - s) / n);
    }

    public static void main(String[] args) throws InterruptedException
    {
        int cores = cpuInfo.getCount();
        boolean loadAvailable = cores > 0 && cpuInfo.getBusy(0) >= 0;
        if (cores <= 0)
            cores = Runtime.getRuntime().availableProcessors();

        System.out.println("LOAD AND PARALLELISM (speedup test)");
        System.out.println();
        System.out.println("CPU model : " + cpuInfo.getModel());
        System.out.println("Cores     : " + cores);
        System.out.println("Task      : count the primes below " + LIMIT);
        System.out.println();

        // Warm-up: a short run so Java has compiled the code before we time it.
        countPrimes(1, 200000);

        int runs = THREAD_COUNTS.length;
        long[] bestTime = new long[runs];
        double[][] coreLoad = new double[runs][cores];
        double[] averageLoad = new double[runs];
        int expected = -1;
        boolean allCorrect = true;

        for (int i = 0; i < runs; i++) {
            bestTime[i] = Long.MAX_VALUE;

            for (int repeat = 0; repeat < REPEATS; repeat++) {
                // Read the CPU counters, run the job, read the counters again.
                // The difference is the load while our threads were running.
                long[] busyBefore = new long[cores];
                long[] idleBefore = new long[cores];
                if (loadAvailable) {
                    busyBefore = readBusy(cores);
                    idleBefore = readIdle(cores);
                }

                long startTime = System.nanoTime();
                int primes = countPrimes(THREAD_COUNTS[i], LIMIT);
                long elapsed = System.nanoTime() - startTime;

                long[] busyAfter = new long[cores];
                long[] idleAfter = new long[cores];
                if (loadAvailable) {
                    busyAfter = readBusy(cores);
                    idleAfter = readIdle(cores);
                }

                // Every run must find the same number of primes, whatever the
                // number of threads. If not, the threads have a bug.
                if (expected == -1)
                    expected = primes;
                if (primes != expected)
                    allCorrect = false;

                // Keep the fastest run and the load that was measured during it.
                if (elapsed < bestTime[i]) {
                    bestTime[i] = elapsed;

                    long busyTotal = 0;
                    long allTotal = 0;
                    for (int core = 0; core < cores; core++) {
                        long busy = busyAfter[core] - busyBefore[core];
                        long idle = idleAfter[core] - idleBefore[core];
                        if (busy + idle > 0)
                            coreLoad[i][core] = busy * 100.0 / (busy + idle);
                        else
                            coreLoad[i][core] = 0.0;
                        busyTotal = busyTotal + busy;
                        allTotal = allTotal + busy + idle;
                    }
                    if (allTotal > 0)
                        averageLoad[i] = busyTotal * 100.0 / allTotal;
                    else
                        averageLoad[i] = 0.0;
                }
            }
        }

        double s = 1.0;
        int sizeUsed = 1;
        for (int i = 1; i < runs; i++) {
            int n = THREAD_COUNTS[i];
            if (n > cores)
                continue;
            double speedup = (double) bestTime[0] / bestTime[i];
            double estimate = (1.0 / speedup - 1.0 / n) / (1.0 - 1.0 / n);
            if (estimate < 0.0)
                estimate = 0.0;
            if (estimate > 1.0)
                estimate = 1.0;
            if (estimate < s) {
                s = estimate;
                sizeUsed = n;
            }
        }

        System.out.println("Primes found: " + expected
                           + (allCorrect ? "  (same answer on every run)" : "  WARNING: answers differed!"));
        System.out.println();
        System.out.println("Threads  Time (ms)  Speedup  Amdahl predicts  Efficiency  Average CPU load");
        System.out.println("-------  ---------  -------  ---------------  ----------  ----------------");
        for (int i = 0; i < runs; i++) {
            int n = THREAD_COUNTS[i];
            double ms = bestTime[i] / 1000000.0;
            double speedup = (double) bestTime[0] / bestTime[i];
            double efficiency = speedup / n * 100.0;
            String load = loadAvailable ? String.format("%.1f%%", averageLoad[i]) : "n/a";
            System.out.printf("%-7d  %9.1f  %7.2f  %15.2f  %9.0f%%  %s%n",
                              n, ms, speedup, amdahl(s, n), efficiency, load);
        }

        if (loadAvailable) {
            System.out.println();
            System.out.println("Load on each core while the threads were running:");
            for (int i = 0; i < runs; i++) {
                System.out.printf("  %d thread(s):", THREAD_COUNTS[i]);
                for (int core = 0; core < cores; core++)
                    System.out.printf(" %5.1f%%", coreLoad[i][core]);
                System.out.println();
            }
        } else {
            System.out.println();
            System.out.println("Per-core load is not available here (no /proc/stat).");
        }

        System.out.println();
        System.out.printf("Estimated serial fraction (from the %d thread run): %.3f%n", sizeUsed, s);
        if (s > 0.0)
            System.out.printf("Amdahl's law says the speedup can never go above %.1f, however many threads.%n", 1.0 / s);
        else
            System.out.println("The serial fraction is about 0, so Amdahl's law sets almost no limit here.");
        System.out.println("This computer has " + cores + " cores, so more than " + cores
                           + " threads can not run at the same time.");
        System.out.println("Amdahl's law does not know about the number of cores, so its prediction");
        System.out.println("goes above the measured speedup once there are more threads than cores.");
    }
}