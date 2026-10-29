import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs a heavy computational load across 1, 2, 4, and maximum system threads.
 * Measures real-world execution scaling profiles against Amdahl's Law.
 */
public class ParallelBenchmark {
    
    // Total workload complexity size (adjust if your machine runs too fast/slow)
    private static final long TOTAL_WORK = 4_000_000_000L;

    public static void runBenchmark() {
        int maxCores = cpuInfo.getCount();
        int[] threadConfigs = {1, 2, 4, maxCores};
        
        System.out.println("\n==================================================");
        System.out.println("        PARALLELISM & AMDAHL'S LAW BENCHMARK      ");
        System.out.println("==================================================");
        System.out.printf("Hardware Architecture detected: %d logical cores.%n", maxCores);

        double sequentialTime = 0.0;

        for (int threads : threadConfigs) {
            // Ensure we don't try running a configuration larger than what's available
            if (threads > maxCores && threads > 4) continue;

            long workPerThread = TOTAL_WORK / threads;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            List<HeavyComputeWorker> tasks = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                tasks.add(new HeavyComputeWorker(workPerThread));
            }

            System.out.printf("%nSpinning up execution grid using [%d] threads... %n", threads);
            
            long startTime = System.nanoTime();
            long verificationSink = 0;
            
            try {
                List<Future<Long>> results = pool.invokeAll(tasks);
                for (Future<Long> res : results) {
                    verificationSink += res.get();
                }
            } catch (Exception e) {
                System.err.println("Thread execution failure: " + e.getMessage());
            } finally {
                pool.shutdown();
            }

            long endTime = System.nanoTime();
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            
            if (threads == 1) {
                sequentialTime = durationSeconds;
            }

            double speedup = sequentialTime / durationSeconds;
            
             //Theoretical estimation assuming a 95% parallelizable workload code fraction (P = 0.95)
            double amdahlTheoretical = 1.0 / ((1.0 - 0.95) + (0.95 / threads));
		
	
            System.out.printf("   Execution Time      : %.4f seconds%n", durationSeconds);
            System.out.printf("   Measured Speedup    : %.2fx%n", speedup);
            System.out.printf("   Amdahl Theoretical  : %.2fx (Assumed 95%% parallel fraction)%n", amdahlTheoretical);
            System.out.printf("   [JIT Verification]  : %d%n", verificationSink);
        }
        System.out.println("==================================================");
    }
}

