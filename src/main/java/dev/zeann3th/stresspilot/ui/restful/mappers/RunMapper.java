package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.infrastructure.configs.MapstructConfig;
import dev.zeann3th.stresspilot.ui.restful.dtos.runs.RunResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapstructConfig.class)
public interface RunMapper {
    @Mapping(target = "flowId", source = "flow.id")
    RunResponseDTO toDTO(RunEntity entity);

    List<RunResponseDTO> toDTOList(List<RunEntity> entityList);
}
