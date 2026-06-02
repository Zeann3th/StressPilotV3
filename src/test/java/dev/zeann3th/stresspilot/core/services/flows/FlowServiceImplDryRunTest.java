package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.DryRunStepCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.DryRunStepResult;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.ports.store.EndpointStore;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentVariableStore;
import dev.zeann3th.stresspilot.core.ports.store.FlowStepStore;
import dev.zeann3th.stresspilot.core.ports.store.FlowStore;
import dev.zeann3th.stresspilot.core.ports.store.ProjectStore;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandler;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandlerFactory;
import dev.zeann3th.stresspilot.core.services.flows.nodes.NodeHandlerResult;
import dev.zeann3th.stresspilot.core.services.flows.strategies.distributed.DistributedEventPublisher;
import dev.zeann3th.stresspilot.core.utils.SnowflakeId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowServiceImplDryRunTest {

    @Test
    @SuppressWarnings("unchecked")
    void dryRunStepExecutesOneStepWithTemporaryVariablesWithoutCreatingRun() {
        FlowStore flowStore = mock(FlowStore.class);
        FlowStepStore flowStepStore = mock(FlowStepStore.class);
        ProjectStore projectStore = mock(ProjectStore.class);
        EnvironmentVariableStore envVarStore = mock(EnvironmentVariableStore.class);
        RunStore runStore = mock(RunStore.class);
        FlowProcessor flowProcessor = new FlowProcessor(new JsonMapper());
        FlowNodeHandlerFactory nodeHandlerFactory = mock(FlowNodeHandlerFactory.class);
        SnowflakeId snowflakeId = mock(SnowflakeId.class);
        ObjectProvider<DistributedEventPublisher> publisherProvider = mock(ObjectProvider.class);

        ProjectEntity project = ProjectEntity.builder()
                .id(20L)
                .activeEnvironment(EnvironmentEntity.builder().id(30L).build())
                .build();
        FlowEntity flow = FlowEntity.builder().id(10L).project(project).type("DEFAULT").build();
        FlowStepEntity selected = FlowStepEntity.builder()
                .id("step-a")
                .type(FlowStepType.BRANCH.name())
                .preProcessor(Map.of("inject", Map.of("token", "dry-token")).toString())
                .postProcessor(Map.of("inject", Map.of("after", "done")).toString())
                .nextIfTrue("step-b")
                .build();
        selected.setPreProcessor("{\"inject\":{\"token\":\"dry-token\"}}");
        selected.setPostProcessor("{\"inject\":{\"after\":\"done\"}}");

        when(flowStore.findById(10L)).thenReturn(Optional.of(flow));
        when(flowStepStore.findAllByFlowIdWithEndpoint(10L)).thenReturn(List.of(selected));
        when(projectStore.findById(20L)).thenReturn(Optional.of(project));
        when(envVarStore.findAllByEnvironmentIdAndActiveTrue(30L)).thenReturn(List.of(
                EnvironmentVariableEntity.builder().key("base").value("env").build()));
        when(snowflakeId.nextId()).thenReturn(123L);
        when(nodeHandlerFactory.getHandler(FlowStepType.BRANCH.name())).thenReturn(new CapturingHandler());

        FlowServiceImpl service = new FlowServiceImpl(
                flowStore,
                flowStepStore,
                projectStore,
                mock(EndpointStore.class),
                envVarStore,
                runStore,
                new JsonMapper(),
                mock(ActiveRunRegistry.class),
                mock(FlowAsyncRunner.class),
                snowflakeId,
                publisherProvider,
                flowProcessor,
                nodeHandlerFactory);

        DryRunStepResult result = service.dryRunStep(10L, DryRunStepCommand.builder()
                .stepId("step-a")
                .variables(Map.of("input", "value"))
                .temporaryVariables(Map.of("temporary", "debug"))
                .build());

        assertThat(result.getStepId()).isEqualTo("step-a");
        assertThat(result.getStepType()).isEqualTo(FlowStepType.BRANCH.name());
        assertThat(result.getNextStepId()).isEqualTo("step-b");
        assertThat(result.getCorrelationId()).isEqualTo("123");
        assertThat(result.getVariables()).containsEntry("base", "env")
                .containsEntry("input", "value")
                .containsEntry("temporary", "debug")
                .containsEntry("token", "dry-token")
                .containsEntry("after", "done");
        assertThat(result.getOutputData()).isEqualTo(Map.of("ok", true));
        assertThat(result.isPersisted()).isFalse();
        verify(runStore, never()).save(any(RunEntity.class));
    }

    private static class CapturingHandler implements FlowNodeHandler {
        @Override
        public String getSupportedType() {
            return FlowStepType.BRANCH.name();
        }

        @Override
        public NodeHandlerResult handle(FlowStepEntity step,
                Map<String, FlowStepEntity> stepMap,
                FlowExecutionContext context) {
            assertThat(context.isPersistRequestLogs()).isFalse();
            assertThat(context.getRun()).isNull();
            Map<String, Object> output = new HashMap<>();
            output.put("ok", true);
            return new NodeHandlerResult(step.getNextIfTrue(), output);
        }
    }
}
