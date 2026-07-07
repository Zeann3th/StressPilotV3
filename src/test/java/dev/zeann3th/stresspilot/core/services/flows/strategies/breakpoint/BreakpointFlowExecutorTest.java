package dev.zeann3th.stresspilot.core.services.flows.strategies.breakpoint;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ConfigKey;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.enums.FlowType;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.configs.ConfigService;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowProcessor;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandler;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandlerFactory;
import dev.zeann3th.stresspilot.core.services.flows.nodes.NodeHandlerResult;
import dev.zeann3th.stresspilot.core.services.flows.nodes.strategies.StartNodeHandler;
import dev.zeann3th.stresspilot.core.utils.SnowflakeId;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BreakpointFlowExecutorTest {

    @Test
    void abortsRunWhenFailureRateBreachesConfiguredThreshold() {
        BreakpointRegistry breakpointRegistry = new BreakpointRegistry();
        ConfigService configService = mock(ConfigService.class);
        FlowProcessor flowProcessor = mock(FlowProcessor.class);
        when(configService.getValue(ConfigKey.BREAKPOINT_ERROR_THRESHOLD.name()))
                .thenReturn(Optional.of("0.5"));
        when(flowProcessor.shouldRun(any(), any(), anyInt())).thenReturn(true);

        BreakpointFlowExecutor executor = new BreakpointFlowExecutor();
        executor.initInfra(
                new ActiveRunRegistry(),
                nodeHandlerFactory(),
                flowProcessor,
                new SnowflakeId());
        ReflectionTestUtils.setField(executor, "breakpointRegistry", breakpointRegistry);
        ReflectionTestUtils.setField(executor, "configService", configService);

        String result = executor.execute(context());

        assertThat(result).isEqualTo(RunStatus.ABORTED.name());
    }

    @Test
    void supportsBreakpointFlowTypeOnly() {
        BreakpointFlowExecutor executor = new BreakpointFlowExecutor();

        assertThat(executor.getType()).isEqualTo(FlowType.BREAKPOINT.name());
        assertThat(executor.supports(FlowType.BREAKPOINT.name())).isTrue();
        assertThat(executor.supports(FlowType.DEFAULT.name())).isFalse();
    }

    private static FlowNodeHandlerFactory nodeHandlerFactory() {
        FlowNodeHandlerFactory factory = new FlowNodeHandlerFactory(List.of(
                new StartNodeHandler(),
                new FailingNodeHandler()));
        factory.init();
        return factory;
    }

    private static FlowExecutionContext context() {
        FlowEntity flow = FlowEntity.builder()
                .id(101L)
                .name("Breakpoint flow")
                .type(FlowType.BREAKPOINT.name())
                .build();
        RunEntity run = RunEntity.builder()
                .id("breakpoint-run-1")
                .flow(flow)
                .status(RunStatus.RUNNING.name())
                .threads(1)
                .duration(60)
                .rampUpDuration(0)
                .startedAt(LocalDateTime.of(2026, 7, 7, 19, 0))
                .build();
        FlowStepEntity start = FlowStepEntity.builder()
                .id("start")
                .flow(flow)
                .type(FlowStepType.START.name())
                .nextIfTrue("fail")
                .build();
        FlowStepEntity fail = FlowStepEntity.builder()
                .id("fail")
                .flow(flow)
                .type(FailingNodeHandler.TYPE)
                .build();

        return FlowExecutionContext.builder()
                .runId("breakpoint-run-1")
                .run(run)
                .flowType(FlowType.BREAKPOINT.name())
                .steps(List.of(start, fail))
                .command(RunFlowCommand.builder()
                        .threads(1)
                        .totalDuration(60)
                        .rampUpDuration(0)
                        .build())
                .baseEnvironment(Map.of())
                .build();
    }

    private static final class FailingNodeHandler implements FlowNodeHandler {
        private static final String TYPE = "FAILING_TEST";

        @Override
        public String getSupportedType() {
            return TYPE;
        }

        @Override
        public NodeHandlerResult handle(FlowStepEntity step,
                Map<String, FlowStepEntity> stepMap,
                FlowExecutionContext context) {
            context.recordRequest(false);
            return NodeHandlerResult.of(null);
        }
    }
}
