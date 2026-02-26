package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.CreateFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.FlowStepCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface FlowService {
    Page<FlowEntity> getListFlow(Long projectId, String name, Pageable pageable);

    FlowEntity getFlowDetail(Long flowId);

    FlowEntity createFlow(CreateFlowCommand createFlowCommand);

    FlowEntity updateFlow(Long flowId, Map<String, Object> patch);

    void deleteFlow(Long flowId);

    FlowEntity configureFlow(Long flowId, List<FlowStepCommand> steps);

    void runFlow(Long flowId, RunFlowCommand runFlowCommand);

    RunEntity getLastRun(Long flowId);
}
