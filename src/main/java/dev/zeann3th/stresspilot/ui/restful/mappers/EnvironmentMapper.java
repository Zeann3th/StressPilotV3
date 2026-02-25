package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.environment.UpdateEnvironmentVariablesCommand;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.infrastructure.configs.MapstructConfig;
import dev.zeann3th.stresspilot.ui.restful.dtos.environments.EnvironmentVariableDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.environments.UpdateEnvironmentRequestDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapstructConfig.class)
public interface EnvironmentMapper {
    @Mapping(target = "environmentId", source = "environment.id")
    @Mapping(target = "isActive", source = "active")
    EnvironmentVariableDTO toDTO(EnvironmentVariableEntity entity);

    List<EnvironmentVariableDTO> toDTOList(List<EnvironmentVariableEntity> entityList);

    @Mapping(target = "environmentId", source = "environmentId")
    @Mapping(target = "added", source = "dto.added")
    @Mapping(target = "updated", source = "dto.updated")
    @Mapping(target = "removed", source = "dto.removed")
    UpdateEnvironmentVariablesCommand toUpdateCommand(Long environmentId, UpdateEnvironmentRequestDTO dto);

    UpdateEnvironmentVariablesCommand.Add toAddCommand(UpdateEnvironmentRequestDTO.Add dto);
    
    @Mapping(target = "isActive", source = "isActive")
    UpdateEnvironmentVariablesCommand.Update toUpdateCommand(UpdateEnvironmentRequestDTO.Update dto);
}
