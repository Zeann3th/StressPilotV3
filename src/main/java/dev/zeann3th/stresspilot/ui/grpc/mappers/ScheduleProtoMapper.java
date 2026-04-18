package dev.zeann3th.stresspilot.ui.grpc.mappers;

import dev.zeann3th.stresspilot.core.domain.entities.ScheduleEntity;
import dev.zeann3th.stresspilot.grpc.ui.CreateScheduleRequest;
import dev.zeann3th.stresspilot.grpc.ui.ListSchedulesResponse;
import dev.zeann3th.stresspilot.grpc.ui.ScheduleResponse;
import dev.zeann3th.stresspilot.infrastructure.configs.mappers.MapstructProtoConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapstructProtoConfig.class)
public interface ScheduleProtoMapper {

    @Mapping(target = "flowId", source = "flow.id")
    @Mapping(expression = "java(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : \"\")", target = "createdAt")
    @Mapping(expression = "java(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : \"\")", target = "updatedAt")
    ScheduleResponse toProto(ScheduleEntity entity);

    @org.mapstruct.BeanMapping(ignoreByDefault = true)
    @Mapping(target = "flow.id", source = "flowId")
    @Mapping(target = "quartzExpr", source = "quartzExpr")
    @Mapping(target = "threads", source = "threads")
    @Mapping(target = "duration", source = "duration")
    @Mapping(target = "rampUp", source = "rampUp")
    @Mapping(target = "enabled", source = "enabled")
    ScheduleEntity toEntity(CreateScheduleRequest request);

    default ListSchedulesResponse toListProto(List<ScheduleEntity> entities, long totalElements, int totalPages) {
        return ListSchedulesResponse.newBuilder()
                .addAllSchedules(entities.stream().map(this::toProto).toList())
                .setTotalElements(totalElements)
                .setTotalPages(totalPages)
                .build();
    }
}
