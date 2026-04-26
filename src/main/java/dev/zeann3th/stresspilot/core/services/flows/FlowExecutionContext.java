package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
@Builder(toBuilder = true)
public class FlowExecutionContext {

    // Shared / Base Data
    private String runId;
    private RunEntity run;
    private String flowType;
    private List<FlowStepEntity> steps;
    private RunFlowCommand command;
    private Map<String, Object> baseEnvironment;
    private AtomicBoolean stopSignal;
    private long deadline;

    // Thread-Local Data
    @Builder.Default private int threadId = 0;
    @Builder.Default private Map<String, Object> variables = new ConcurrentHashMap<>();
    @Builder.Default private ExecutionContext executionContext = new BaseExecutionContext();
    @Builder.Default private final AtomicInteger iterationCount = new AtomicInteger(0);
    @Builder.Default private final AtomicLong requestCount = new AtomicLong(0);
    @Builder.Default private final AtomicLong failureCount = new AtomicLong(0);

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
