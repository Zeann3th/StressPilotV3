package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.function.CreateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.commands.function.UpdateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import dev.zeann3th.stresspilot.infrastructure.configs.mappers.MapstructConfig;
import dev.zeann3th.stresspilot.ui.restful.dtos.function.CreateFunctionRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.function.FunctionResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.function.UpdateFunctionRequestDTO;
import org.mapstruct.Mapper;

@Mapper(config = MapstructConfig.class)
public interface FunctionMapper {
    FunctionResponseDTO toResponse(FunctionEntity entity);

    CreateFunctionCommand toCreateCommand(CreateFunctionRequestDTO request);

    UpdateFunctionCommand toUpdateCommand(UpdateFunctionRequestDTO request);
}
