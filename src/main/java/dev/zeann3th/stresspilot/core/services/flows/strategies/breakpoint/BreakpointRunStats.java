package dev.zeann3th.stresspilot.core.services.flows.strategies.breakpoint;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared, thread-safe stats for a single BREAKPOINT run.
 * All worker threads report into the same instance.
 */
public class BreakpointRunStats {

    private static final long MIN_SAMPLES = 10;

    private final AtomicLong total = new AtomicLong(0);
    private final AtomicLong failed = new AtomicLong(0);
    private final double threshold;

    public BreakpointRunStats(double threshold) {
        this.threshold = threshold;
    }

    public void record(long deltaTotal, long deltaFailed) {
        total.addAndGet(deltaTotal);
        failed.addAndGet(deltaFailed);
    }

    /**
     * Returns true when enough samples have been collected and the failure rate
     * has exceeded the configured threshold.
     */
    public boolean isBreached() {
        long t = total.get();
        return t >= MIN_SAMPLES && (double) failed.get() / t > threshold;
    }

    public long getTotal() { return total.get(); }
    public long getFailed() { return failed.get(); }
    public double getThreshold() { return threshold; }
}