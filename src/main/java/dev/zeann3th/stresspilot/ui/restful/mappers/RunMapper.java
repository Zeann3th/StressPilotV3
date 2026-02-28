package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.ui.restful.dtos.run.RunResponseDTO;
import org.mapstruct.*;

@Mapper(config = dev.zeann3th.stresspilot.infrastructure.configs.MapstructConfig.class)
public interface RunMapper {

    @Mapping(source = "flow.id", target = "flowId")
    RunResponseDTO toResponse(RunEntity entity);
}
