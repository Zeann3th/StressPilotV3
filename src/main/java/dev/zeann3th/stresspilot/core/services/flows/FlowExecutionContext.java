package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class FlowExecutionContext {

    private int threadId;
    private String runId;
    private RunEntity run;
    private Map<String, Object> variables = new ConcurrentHashMap<>();
    private ExecutionContext executionContext;
    private final AtomicInteger iterationCount = new AtomicInteger(0);
    private final AtomicLong requestCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private AtomicBoolean stopSignal;
    private long deadline;

    public boolean shouldStop() {
        return (stopSignal != null && stopSignal.get())
                || System.currentTimeMillis() >= deadline
                || Thread.currentThread().isInterrupted();
    }

    public void incrementIteration() {
        iterationCount.incrementAndGet();
    }

    public int getIterationCount() {
        return iterationCount.get();
    }

    public void recordRequest(boolean success) {
        requestCount.incrementAndGet();
        if (!success) failureCount.incrementAndGet();
    }

    public long getRequestCount() {
        return requestCount.get();
    }

    public long getFailureCount() {
        return failureCount.get();
    }
}