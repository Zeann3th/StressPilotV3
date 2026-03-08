package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.infrastructure.configs.mappers.MapstructConfig;
import dev.zeann3th.stresspilot.ui.restful.dtos.run.RunResponseDTO;
import org.mapstruct.*;

@Mapper(config = MapstructConfig.class)
public interface RunMapper {

    RunResponseDTO toResponse(RunEntity entity);
}
