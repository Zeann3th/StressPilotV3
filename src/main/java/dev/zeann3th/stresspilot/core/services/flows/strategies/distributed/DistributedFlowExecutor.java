package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowType;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class DistributedFlowExecutor extends FlowExecutor {
    private final DistributedWorkerRegistry workerRegistry;
    private final DistributedEventPublisher eventPublisher;
    private final String nodeId;
    private final long waitPollMs;

    public DistributedFlowExecutor(
            ObjectProvider<DistributedWorkerRegistry> workerRegistryProvider,
            DistributedEventPublisher eventPublisher,
            @Value("${application.distributed.node-id:local}") String nodeId,
            @Value("${application.distributed.master-wait-poll-ms:100}") long waitPollMs
    ) {
        this(workerRegistryProvider.getIfAvailable(), eventPublisher, nodeId, waitPollMs);
    }

    DistributedFlowExecutor(
            DistributedWorkerRegistry workerRegistry,
            DistributedEventPublisher eventPublisher,
            String nodeId,
            long waitPollMs
    ) {
        this.workerRegistry = workerRegistry;
        this.eventPublisher = eventPublisher;
        this.nodeId = nodeId;
        this.waitPollMs = Math.max(1, waitPollMs);
    }

    @Override
    public String getType() {
        return FlowType.DISTRIBUTED.name();
    }

    @Override
    public boolean supports(String type) {
        return FlowType.DISTRIBUTED.name().equals(type);
    }

    @Override
    public String execute(FlowExecutionContext baseContext) {
        String runId = baseContext.getRunId();
        RunFlowCommand cmd = baseContext.getCommand();
        AtomicBoolean stopSignal = activeRunRegistry.registerRun(runId);

        int threads = Math.max(1, cmd.getThreads());
        long totalMs = (long) cmd.getTotalDuration() * 1000;
        long deadline = System.currentTimeMillis() + totalMs;
        baseContext.setStopSignal(stopSignal);
        baseContext.setDeadline(deadline);

        try {
            List<String> workers = availableWorkers();
            if (workers.isEmpty()) {
                log.warn("Distributed run {} aborted because no worker nodes are active", runId);
                return RunStatus.ABORTED.name();
            }

            int assignedWorkerCount = Math.min(threads, workers.size());
            List<String> assignedWorkers = workers.subList(0, assignedWorkerCount);
            List<Integer> threadAssignments = splitThreads(threads, assignedWorkers.size());
            try {
                for (int i = 0; i < assignedWorkers.size(); i++) {
                    eventPublisher.publishWorkload(DistributedEventPublisher.WorkloadPayload.from(
                            baseContext,
                            assignedWorkers.get(i),
                            threadAssignments.get(i)));
                }
            } catch (RuntimeException e) {
                log.warn("Distributed run {} aborted because workload dispatch failed: {}", runId, e.getMessage());
                return RunStatus.ABORTED.name();
            }

            waitForDeadlineOrStop(stopSignal, deadline);
            return stopSignal.get() && System.currentTimeMillis() < deadline
                    ? RunStatus.ABORTED.name()
                    : RunStatus.COMPLETED.name();
        } finally {
            activeRunRegistry.deregisterRun(runId);
        }
    }

    private List<String> availableWorkers() {
        Set<String> activeNodeIds = workerRegistry != null
                ? workerRegistry.activeNodeIds()
                : Set.of();
        LinkedHashSet<String> workers = new LinkedHashSet<>(activeNodeIds);
        workers.remove(nodeId);
        return new ArrayList<>(workers);
    }

    private List<Integer> splitThreads(int threads, int workerCount) {
        int base = threads / workerCount;
        int remainder = threads % workerCount;
        List<Integer> assignments = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            assignments.add(base + (i < remainder ? 1 : 0));
        }
        return assignments;
    }

    private void waitForDeadlineOrStop(AtomicBoolean stopSignal, long deadline) {
        while (!stopSignal.get() && System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            try {
                Thread.sleep(Math.min(waitPollMs, Math.max(1, remaining)));
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                stopSignal.set(true);
                return;
            }
        }
    }

    @Override
    protected void executeWorker(FlowExecutionContext ctx, Map<String, FlowStepEntity> stepMap) {
        throw new UnsupportedOperationException("Distributed master does not execute local worker loops");
    }
}
