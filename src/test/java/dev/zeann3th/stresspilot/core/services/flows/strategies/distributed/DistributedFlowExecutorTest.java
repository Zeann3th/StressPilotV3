package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.enums.FlowType;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DistributedFlowExecutorTest {

    @Test
    void supportsDistributedFlowType() {
        DistributedFlowExecutor executor = executorWith(SetFixture.empty());

        assertThat(FlowType.DISTRIBUTED.name()).isEqualTo("DISTRIBUTED");
        assertThat(executor.getType()).isEqualTo(FlowType.DISTRIBUTED.name());
        assertThat(executor.supports(FlowType.DISTRIBUTED.name())).isTrue();
        assertThat(executor.supports(FlowType.DEFAULT.name())).isFalse();
    }

    @Test
    void noWorkersReturnsAbortedAndDeregistersRun() {
        SetFixture fixture = SetFixture.empty();
        RecordingActiveRunRegistry activeRunRegistry = new RecordingActiveRunRegistry();
        DistributedFlowExecutor executor = executorWith(fixture);
        executor.initInfra(activeRunRegistry, null);

        String result = executor.execute(context(1, 0));

        assertThat(result).isEqualTo(RunStatus.ABORTED.name());
        assertThat(activeRunRegistry.events).containsExactly("register:run-1", "deregister:run-1");
        assertThat(activeRunRegistry.hasActiveRuns()).isFalse();
        verifyNoInteractions(fixture.publisher);
    }

    @Test
    void splitsThreadsEvenlyAcrossWorkersAndPublishesWorkloads() {
        SetFixture fixture = SetFixture.withWorkers("worker-a", "worker-b");
        DistributedFlowExecutor executor = executorWith(fixture);
        executor.initInfra(new RecordingActiveRunRegistry(), null);

        String result = executor.execute(context(5, 0));

        assertThat(result).isEqualTo(RunStatus.COMPLETED.name());
        ArgumentCaptor<DistributedEventPublisher.WorkloadPayload> captor =
                ArgumentCaptor.forClass(DistributedEventPublisher.WorkloadPayload.class);
        verify(fixture.publisher, times(2)).publishWorkload(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(DistributedEventPublisher.WorkloadPayload::targetNodeId,
                        DistributedEventPublisher.WorkloadPayload::assignedThreads)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("worker-a", 3),
                        org.assertj.core.groups.Tuple.tuple("worker-b", 2));
    }

    @Test
    void excludesLocalMasterNodeFromAssignedWorkloads() {
        SetFixture fixture = SetFixture.withWorkers("node-master", "worker-a", "worker-b");
        DistributedFlowExecutor executor = executorWith(fixture);
        executor.initInfra(new RecordingActiveRunRegistry(), null);

        executor.execute(context(5, 0));

        ArgumentCaptor<DistributedEventPublisher.WorkloadPayload> captor =
                ArgumentCaptor.forClass(DistributedEventPublisher.WorkloadPayload.class);
        verify(fixture.publisher, times(2)).publishWorkload(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(DistributedEventPublisher.WorkloadPayload::targetNodeId)
                .containsExactly("worker-a", "worker-b");
    }

    private static DistributedFlowExecutor executorWith(SetFixture fixture) {
        return new DistributedFlowExecutor(fixture.registry, fixture.publisher, "node-master", 10);
    }

    private static FlowExecutionContext context(int threads, int durationSeconds) {
        FlowEntity flow = FlowEntity.builder()
                .id(101L)
                .name("Distributed flow")
                .type(FlowType.DISTRIBUTED.name())
                .build();
        RunEntity run = RunEntity.builder()
                .id("run-1")
                .flow(flow)
                .status(RunStatus.RUNNING.name())
                .threads(threads)
                .duration(durationSeconds)
                .rampUpDuration(0)
                .startedAt(LocalDateTime.of(2026, 5, 24, 21, 0))
                .build();
        FlowStepEntity start = FlowStepEntity.builder()
                .id("start")
                .flow(flow)
                .type(FlowStepType.START.name())
                .build();

        return FlowExecutionContext.builder()
                .runId("run-1")
                .run(run)
                .flowType(FlowType.DISTRIBUTED.name())
                .steps(List.of(start))
                .command(RunFlowCommand.builder()
                        .threads(threads)
                        .totalDuration(durationSeconds)
                        .rampUpDuration(0)
                        .variables(Map.of("region", "ap-southeast"))
                        .credentials(List.of(Map.of("token", "secret")))
                        .build())
                .baseEnvironment(Map.of("host", "example.test"))
                .variables(new java.util.concurrent.ConcurrentHashMap<>(Map.of("region", "ap-southeast")))
                .build();
    }

    private static final class RecordingActiveRunRegistry extends ActiveRunRegistry {
        private final List<String> events = new ArrayList<>();

        @Override
        public AtomicBoolean registerRun(String runId) {
            events.add("register:" + runId);
            return super.registerRun(runId);
        }

        @Override
        public void deregisterRun(String runId) {
            events.add("deregister:" + runId);
            super.deregisterRun(runId);
        }
    }

    private record SetFixture(
            DistributedWorkerRegistry registry,
            DistributedEventPublisher publisher) {
        static SetFixture empty() {
            return withWorkers();
        }

        static SetFixture withWorkers(String... workers) {
            DistributedWorkerRegistry registry = mock(DistributedWorkerRegistry.class);
            DistributedEventPublisher publisher = mock(DistributedEventPublisher.class);
            when(registry.activeNodeIds()).thenReturn(new LinkedHashSet<>(List.of(workers)));
            return new SetFixture(registry, publisher);
        }
    }
}
