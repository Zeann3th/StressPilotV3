package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.flow.*;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.ui.restful.dtos.flow.*;
import org.mapstruct.*;

import java.util.List;

/**
 * Bi-directional mapper for the Flow domain.
 */
@Mapper(config = dev.zeann3th.stresspilot.infrastructure.configs.MapstructConfig.class)
public interface FlowMapper {

    // ─── Entity → DTO ────────────────────────────────────────────────────────

    @Mapping(source = "project.id", target = "projectId")
    FlowResponseDTO toResponse(FlowEntity entity);

    @Mapping(source = "endpoint.id", target = "endpointId")
    FlowStepResponseDTO toStepResponse(FlowStepEntity step);

    // ─── Request → Command ───────────────────────────────────────────────────

    CreateFlowCommand toCreateCommand(CreateFlowRequestDTO request);

    RunFlowCommand toRunCommand(RunFlowRequestDTO request);

    FlowStepCommand toStepCommand(FlowStepRequestDTO request);

    List<FlowStepCommand> toStepCommands(List<FlowStepRequestDTO> requests);
}
