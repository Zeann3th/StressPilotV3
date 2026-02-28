package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.*;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.*;
import org.mapstruct.*;

@Mapper(config = dev.zeann3th.stresspilot.infrastructure.configs.MapstructConfig.class)
public interface EndpointMapper {

    @Mapping(source = "project.id", target = "projectId")
    EndpointResponseDTO toResponse(EndpointEntity entity);

    CreateEndpointCommand toCreateCommand(CreateEndpointRequestDTO request);

    ExecuteEndpointCommand toExecuteCommand(ExecuteEndpointRequestDTO request);

    ExecuteAdhocEndpointCommand toAdhocCommand(ExecuteAdhocEndpointRequestDTO request);
}
