package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.*;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.*;
import org.mapstruct.*;

@Mapper(config = dev.zeann3th.stresspilot.infrastructure.configs.MapstructConfig.class)
public interface EndpointMapper {

    // ─── Entity → DTO ────────────────────────────────────────────────────────

    @Mapping(source = "project.id", target = "projectId")
    EndpointResponseDTO toResponse(EndpointEntity entity);

    // ─── Request → Command ───────────────────────────────────────────────────

    CreateEndpointCommand toCreateCommand(CreateEndpointRequestDTO request);

    ExecuteEndpointCommand toExecuteCommand(ExecuteEndpointRequestDTO request);

    ExecuteAdhocEndpointCommand toAdhocCommand(ExecuteAdhocEndpointRequestDTO request);
}
