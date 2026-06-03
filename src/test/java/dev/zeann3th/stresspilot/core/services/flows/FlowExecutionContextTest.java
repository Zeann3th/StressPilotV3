package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class FlowExecutionContextTest {

    @Test
    void shouldStopWhenPerWorkerLoopLimitIsReached() {
        FlowExecutionContext context = FlowExecutionContext.builder()
                .runId("run-1")
                .command(RunFlowCommand.builder()
                        .threads(1)
                        .totalDuration(null)
                        .loopCount(2)
                        .rampUpDuration(0)
                        .build())
                .deadline(Long.MAX_VALUE)
                .variables(new ConcurrentHashMap<>())
                .build();

        assertThat(context.shouldStop()).isFalse();

        context.incrementIteration();
        assertThat(context.shouldStop()).isFalse();

        context.incrementIteration();
        assertThat(context.shouldStop()).isTrue();
    }

    @Test
    void forkedWorkersTrackLoopLimitsIndependently() {
        FlowExecutionContext baseContext = FlowExecutionContext.builder()
                .runId("run-1")
                .command(RunFlowCommand.builder()
                        .threads(2)
                        .totalDuration(null)
                        .loopCount(1)
                        .rampUpDuration(0)
                        .build())
                .deadline(Long.MAX_VALUE)
                .baseEnvironment(Map.of())
                .variables(new ConcurrentHashMap<>())
                .build();

        FlowExecutionContext firstWorker = baseContext.fork(0, Map.of());
        FlowExecutionContext secondWorker = baseContext.fork(1, Map.of());

        firstWorker.incrementIteration();

        assertThat(firstWorker.shouldStop()).isTrue();
        assertThat(secondWorker.shouldStop()).isFalse();
    }
}
