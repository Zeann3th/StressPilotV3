package dev.zeann3th.stresspilot.core.services.flows.strategies.breakpoint;

import java.util.concurrent.atomic.AtomicLong;

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

    public boolean isBreached() {
        long t = total.get();
        return t >= MIN_SAMPLES && (double) failed.get() / t > threshold;
    }

    public long getFailed() { return failed.get(); }
}
