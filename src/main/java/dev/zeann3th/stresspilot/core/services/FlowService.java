package dev.zeann3th.stresspilot.core.services;

import dev.zeann3th.stresspilot.core.domain.commands.flow.ConfigureFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.CreateFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.FlowStepResponse;
import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.UpdateFlowCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FlowService {
    Page<FlowEntity> getListFlow(Long projectId, String name, Pageable pageable);

    FlowEntity getFlowDetail(Long flowId);

    FlowEntity createFlow(CreateFlowCommand command);

    void deleteFlow(Long flowId);

    FlowEntity updateFlow(UpdateFlowCommand command);

    List<FlowStepResponse> configureFlow(ConfigureFlowCommand command);

    void runFlow(RunFlowCommand command);
}
