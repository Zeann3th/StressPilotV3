package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.entities.ScheduleEntity;
import dev.zeann3th.stresspilot.infrastructure.configs.mappers.MapstructConfig;
import dev.zeann3th.stresspilot.ui.restful.dtos.schedule.CreateScheduleRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.schedule.ScheduleResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapstructConfig.class)
public interface ScheduleMapper {

    @Mapping(target = "flowId", source = "flow.id")
    ScheduleResponseDTO toResponse(ScheduleEntity entity);

    @org.mapstruct.BeanMapping(ignoreByDefault = true)
    @Mapping(target = "flow.id", source = "flowId")
    @Mapping(target = "quartzExpr", source = "quartzExpr")
    @Mapping(target = "threads", source = "threads")
    @Mapping(target = "duration", source = "duration")
    @Mapping(target = "rampUp", source = "rampUp")
    @Mapping(target = "enabled", source = "enabled")
    ScheduleEntity toEntity(CreateScheduleRequestDTO request);
}
