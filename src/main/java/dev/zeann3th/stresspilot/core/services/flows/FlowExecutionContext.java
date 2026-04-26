package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.AccessLevel;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
@Builder(toBuilder = true)
public class FlowExecutionContext {

    // Shared / Base Data - Effectively Immutable for handlers
    private final String runId;
    private final RunEntity run;
    private final String flowType;
    
    @Getter(AccessLevel.NONE)
    private final List<FlowStepEntity> steps;
    
    private final RunFlowCommand command;
    
    @Getter(AccessLevel.NONE)
    private final Map<String, Object> baseEnvironment;
    
    private AtomicBoolean stopSignal;
    private long deadline;

    // Thread-Local Data
    @Builder.Default private int threadId = 0;
    @Builder.Default private Map<String, Object> variables = new ConcurrentHashMap<>();
    @Builder.Default private ExecutionContext executionContext = new BaseExecutionContext();
    @Builder.Default private final AtomicInteger iterationCount = new AtomicInteger(0);
    @Builder.Default private final AtomicLong requestCount = new AtomicLong(0);
    @Builder.Default private final AtomicLong failureCount = new AtomicLong(0);

    public List<FlowStepEntity> getSteps() {
        return steps != null ? Collections.unmodifiableList(steps) : Collections.emptyList();
    }

    public Map<String, Object> getBaseEnvironment() {
        return baseEnvironment != null ? Collections.unmodifiableMap(baseEnvironment) : Collections.emptyMap();
    }

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

    public FlowExecutionContext fork(int threadId, Map<String, Object> threadVariables) {
        return this.toBuilder()
                .threadId(threadId)
                .variables(new ConcurrentHashMap<>(threadVariables))
                .executionContext(new BaseExecutionContext())
                .iterationCount(new AtomicInteger(0))
                .requestCount(new AtomicLong(0))
                .failureCount(new AtomicLong(0))
                .build();
    }
}
