package dev.zeann3th.stresspilot.ui.grpc.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.environment.UpdateEnvironmentVariablesCommand;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.infrastructure.configs.mappers.MapstructProtoConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapstructProtoConfig.class)
public interface EnvironmentProtoMapper {

    @Mapping(expression = "java(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : \"\")", target = "createdAt")
    @Mapping(expression = "java(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : \"\")", target = "updatedAt")
    EnvironmentVariableResponse toProto(EnvironmentVariableEntity entity);

    default UpdateEnvironmentVariablesCommand toUpdateCommand(UpdateEnvironmentVariablesRequest request) {
        List<UpdateEnvironmentVariablesCommand.Update> updated = request.getVariablesList().stream()
                .map(e -> new UpdateEnvironmentVariablesCommand.Update(null, e.getKey(), e.getValue(), false))
                .toList();

        return UpdateEnvironmentVariablesCommand.builder()
                .updated(updated)
                .removed(java.util.List.of())
                .added(java.util.List.of())
                .build();
    }

    default ListEnvironmentVariablesResponse toListProto(List<EnvironmentVariableEntity> entities) {
        return ListEnvironmentVariablesResponse.newBuilder()
                .addAllVariables(entities.stream().map(this::toProto).toList())
                .build();
    }
}
