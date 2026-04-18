package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FlowAsyncRunner {

    private final FlowExecutorFactory flowExecutorFactory;

    @Async
    public void run(String runId, FlowEntity flow, List<FlowStepEntity> steps, RunFlowCommand cmd) {
        flowExecutorFactory.getStrategy(flow.getType()).execute(runId, flow, steps, cmd);
    }
}
