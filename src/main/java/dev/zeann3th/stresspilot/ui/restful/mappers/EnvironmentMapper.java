package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.environment.UpdateEnvironmentVariablesCommand;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.ui.restful.dtos.environment.*;
import org.mapstruct.*;

@Mapper(config = dev.zeann3th.stresspilot.infrastructure.configs.MapstructConfig.class)
public interface EnvironmentMapper {

    EnvironmentVariableResponseDTO toResponse(EnvironmentVariableEntity entity);

    UpdateEnvironmentVariablesCommand toUpdateCommand(UpdateEnvironmentVariablesRequestDTO request);

    UpdateEnvironmentVariablesCommand.Update toUpdate(UpdateEnvironmentVariablesRequestDTO.UpdateEntry entry);

    UpdateEnvironmentVariablesCommand.Add toAdd(UpdateEnvironmentVariablesRequestDTO.AddEntry entry);
}
