package dev.zeann3th.stresspilot.core.services.flows.strategies.breakpoint;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BreakpointRegistry {

    private final Map<String, BreakpointRunStats> stats = new ConcurrentHashMap<>();

    public void register(String runId, double threshold) {
        stats.put(runId, new BreakpointRunStats(threshold));
    }

    public void deregister(String runId) {
        stats.remove(runId);
    }

    public void record(String runId, long deltaTotal, long deltaFailed) {
        BreakpointRunStats s = stats.get(runId);
        if (s != null) s.record(deltaTotal, deltaFailed);
    }

    public boolean isBreached(String runId) {
        BreakpointRunStats s = stats.get(runId);
        return s != null && s.isBreached();
    }
}
