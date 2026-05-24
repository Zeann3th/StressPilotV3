package dev.zeann3th.stresspilot.core.services.flows.strategies.distributed;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.enums.EndpointType;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.enums.FlowType;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedWorkerSubscriberTest {
    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    void targetedWorkRegistersExecutesAssignedThreadsAsDistributedWorkerAndDeregisters() {
        RecordingActiveRunRegistry registry = new RecordingActiveRunRegistry();
        List<FlowExecutionContext> executed = new ArrayList<>();
        DistributedWorkerSubscriber subscriber = new DistributedWorkerSubscriber(
                registry,
                "worker-a",
                "stresspilot",
                jsonMapper,
                executed::add);

        subscriber.handleWorkMessage(jsonMapper.writeValueAsString(workload("worker-a", 3)));

        assertThat(registry.events).containsExactly("register:run-1", "deregister:run-1");
        assertThat(registry.hasActiveRuns()).isFalse();
        assertThat(executed).hasSize(1);

        FlowExecutionContext context = executed.getFirst();
        assertThat(context.isDistributedWorker()).isTrue();
        assertThat(context.getCommand().getThreads()).isEqualTo(3);
        assertThat(context.getRunId()).isEqualTo("run-1");
        assertThat(context.getRun().getFlowId()).isEqualTo(101L);
        assertThat(context.getDeadline()).isEqualTo(123456789L);
        assertThat(context.getBaseEnvironment()).containsEntry("host", "example.test");
        assertThat(context.getCommand().getVariables()).containsEntry("region", "ap-southeast");
        assertThat(context.getCommand().getCredentials()).containsExactly(Map.of("token", "secret"));
        assertThat(context.getSteps())
                .extracting(FlowStepEntity::getId, FlowStepEntity::getType, FlowStepEntity::getNextIfTrue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("start", FlowStepType.START.name(), "endpoint-1"),
                        org.assertj.core.groups.Tuple.tuple("endpoint-1", FlowStepType.ENDPOINT.name(), null));
        EndpointEntity endpoint = context.getSteps().get(1).getEndpoint();
        assertThat(endpoint.getId()).isEqualTo(11L);
        assertThat(endpoint.getType()).isEqualTo(EndpointType.HTTP.name());
        assertThat(endpoint.getUrl()).isEqualTo("https://example.test/api");
    }

    @Test
    void nonTargetedWorkIsIgnored() {
        RecordingActiveRunRegistry registry = new RecordingActiveRunRegistry();
        List<FlowExecutionContext> executed = new ArrayList<>();
        DistributedWorkerSubscriber subscriber = new DistributedWorkerSubscriber(
                registry,
                "worker-a",
                "stresspilot",
                jsonMapper,
                executed::add);

        subscriber.handleWorkMessage(jsonMapper.writeValueAsString(workload("worker-b", 3)));

        assertThat(registry.events).isEmpty();
        assertThat(registry.hasActiveRuns()).isFalse();
        assertThat(executed).isEmpty();
    }

    @Test
    void zeroOrNegativeAssignedThreadsAreIgnored() {
        RecordingActiveRunRegistry registry = new RecordingActiveRunRegistry();
        List<FlowExecutionContext> executed = new ArrayList<>();
        DistributedWorkerSubscriber subscriber = new DistributedWorkerSubscriber(
                registry,
                "worker-a",
                "stresspilot",
                jsonMapper,
                executed::add);

        subscriber.handleWorkMessage(jsonMapper.writeValueAsString(workload("worker-a", 0)));

        assertThat(registry.events).isEmpty();
        assertThat(registry.hasActiveRuns()).isFalse();
        assertThat(executed).isEmpty();
    }

    @Test
    void stopMessageInterruptsRunLocally() {
        RecordingActiveRunRegistry registry = new RecordingActiveRunRegistry();
        registry.registerRun("run-1");
        DistributedWorkerSubscriber subscriber = new DistributedWorkerSubscriber(
                registry,
                "worker-a",
                "stresspilot",
                jsonMapper,
                _ -> {});

        subscriber.handleStopMessage(jsonMapper.writeValueAsString(new DistributedEventPublisher.StopPayload("run-1")));

        assertThat(registry.events).containsExactly("register:run-1", "interrupt:run-1");
        assertThat(registry.stopSignal("run-1")).isTrue();
    }

    private static DistributedEventPublisher.WorkloadPayload workload(String targetNodeId, int assignedThreads) {
        return new DistributedEventPublisher.WorkloadPayload(
                "DISTRIBUTED_WORKLOAD",
                "run-1",
                101L,
                targetNodeId,
                assignedThreads,
                60,
                5,
                123456789L,
                FlowType.DISTRIBUTED.name(),
                LocalDateTime.of(2026, 5, 24, 21, 0),
                Map.of("region", "ap-southeast"),
                List.of(Map.of("token", "secret")),
                Map.of("host", "example.test"),
                List.of(
                        new DistributedEventPublisher.FlowStepPayload(
                                "start",
                                FlowStepType.START.name(),
                                null,
                                null,
                                null,
                                null,
                                "endpoint-1",
                                null,
                                null),
                        new DistributedEventPublisher.FlowStepPayload(
                                "endpoint-1",
                                FlowStepType.ENDPOINT.name(),
                                11L,
                                new DistributedEventPublisher.EndpointPayload(
                                        11L,
                                        "HTTP endpoint",
                                        "description",
                                        EndpointType.HTTP.name(),
                                        "https://example.test/api",
                                        "{}",
                                        "status == 200",
                                        "GET",
                                        "{}",
                                        "{}",
                                        null,
                                        null,
                                        null),
                                null,
                                null,
                                null,
                                null,
                                null)));
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

        @Override
        public boolean interruptRun(String runId) {
            events.add("interrupt:" + runId);
            return super.interruptRun(runId);
        }
    }
}
