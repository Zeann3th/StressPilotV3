package dev.zeann3th.stresspilot.core.services.flows.strategies.breakpoint;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks error-rate stats for active BREAKPOINT runs.
 * Worker threads report request deltas after each iteration;
 * when the threshold is breached the executor sets the run's stop signal.
 */
@Component
public class BreakpointRegistry {

    private final Map<String, BreakpointRunStats> stats = new ConcurrentHashMap<>();

    public void register(String runId, double threshold) {
        stats.put(runId, new BreakpointRunStats(threshold));
    }

    public void deregister(String runId) {
        stats.remove(runId);
    }

    /**
     * Records a delta (requests completed in the last iteration) for the given run.
     * Safe to call from multiple threads concurrently.
     */
    public void record(String runId, long deltaTotal, long deltaFailed) {
        BreakpointRunStats s = stats.get(runId);
        if (s != null) s.record(deltaTotal, deltaFailed);
    }

    /**
     * Returns true if the run's error rate has exceeded its threshold.
     */
    public boolean isBreached(String runId) {
        BreakpointRunStats s = stats.get(runId);
        return s != null && s.isBreached();
    }
}