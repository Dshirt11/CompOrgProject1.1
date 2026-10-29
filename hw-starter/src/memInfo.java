import java.nio.file.Path;

/**
 * Information about memory, read from /proc/meminfo. All sizes are in KiB
 * (1 KiB = 1024 bytes), which is the unit Linux uses in that file.
 */
public class memInfo
{
    // Keep the path in one place so all memory queries use the same test-root behavior.
    private static final Path MEMINFO = sysFile.path("/proc/meminfo");

    /** Total installed memory in KiB, or -1 if it cannot be read. */
    public static long getTotal()
    {
        return field("MemTotal");
    }

    /** Memory that programs could use right now (free plus easily reclaimed), in KiB. */
    public static long getAvailable()
    {
        return field("MemAvailable");
    }

    /** Memory in use in KiB: total minus available. */
    public static long getUsed()
    {
        // Linux reports available memory directly; used memory is the remainder.
        long total = getTotal();
        long available = getAvailable();
        return (total < 0 || available < 0) ? -1 : total - available;
    }
     /** Memory usage as a percentage of total memory. */
    
    public static double getUsedPercentage()
    {
        long total = getTotal();
        long used = getUsed();

        // A percentage is undefined if either reading is missing or total is zero.
        if (total <= 0 || used < 0) {
            return -1;
        }

        // Use floating-point arithmetic so the result includes fractional percent.
        return (used * 100.0) / total;
    }

    /** The value in KiB of a named line of /proc/meminfo such as "SwapTotal", or -1. */
    public static long field(String name)
    {
        for (String line : sysFile.lines(MEMINFO)) {
            if (line.startsWith(name + ":")) {
                // The first token after the field name is its value in KiB.
                String[] part = line.substring(name.length() + 1).strip().split("\\s+");
                try {
                    return Long.parseLong(part[0]);
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }
}

