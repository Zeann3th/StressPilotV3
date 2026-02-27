package dev.zeann3th.stresspilot.ui.grpc.mappers;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.grpc.ui.ListRunsResponse;
import dev.zeann3th.stresspilot.grpc.ui.RunResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = dev.zeann3th.stresspilot.infrastructure.configs.MapstructProtoConfig.class)
public interface RunProtoMapper {

    @Mapping(source = "flow.id", target = "flowId")
    @Mapping(expression = "java(entity.getStartedAt()   != null ? entity.getStartedAt().toString()   : \"\")", target = "startedAt")
    @Mapping(expression = "java(entity.getCompletedAt() != null ? entity.getCompletedAt().toString() : \"\")", target = "completedAt")
    RunResponse toProto(RunEntity entity);

    default ListRunsResponse toListProto(List<RunEntity> entities) {
        return ListRunsResponse.newBuilder()
                .addAllRuns(entities.stream().map(this::toProto).toList())
                .build();
    }
}
