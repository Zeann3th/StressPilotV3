package dev.zeann3th.stresspilot.core.services;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ActiveRunRegistry {

    private final Map<String, AtomicBoolean> activeRuns = new ConcurrentHashMap<>();

    public AtomicBoolean registerRun(String runId) {
        AtomicBoolean stopSignal = new AtomicBoolean(false);
        activeRuns.put(runId, stopSignal);
        return stopSignal;
    }

    public void deregisterRun(String runId) {
        activeRuns.remove(runId);
    }

    public boolean interruptRun(String runId) {
        AtomicBoolean stopSignal = activeRuns.get(runId);
        if (stopSignal != null) {
            stopSignal.set(true);
            return true;
        }
        return false;
    }

    public boolean hasActiveRuns() {
        return !activeRuns.isEmpty();
    }
}