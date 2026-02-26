package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Data
public class FlowExecutionContext {

    private int threadId;
    private Long runId;
    private RunEntity run;
    private Map<String, Object> variables = new ConcurrentHashMap<>();
    private ExecutionContext executionContext;
    private final AtomicInteger iterationCount = new AtomicInteger(0);

    public void incrementIteration() {
        iterationCount.incrementAndGet();
    }

    public int getIterationCount() {
        return iterationCount.get();
    }
}
