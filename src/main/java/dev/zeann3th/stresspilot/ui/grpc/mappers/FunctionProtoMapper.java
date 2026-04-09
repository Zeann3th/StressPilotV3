package dev.zeann3th.stresspilot.ui.grpc.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.function.CreateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.commands.function.UpdateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.infrastructure.configs.mappers.MapstructProtoConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapstructProtoConfig.class)
public interface FunctionProtoMapper {

    @Mapping(expression = "java(entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : \"\")", target = "createdAt")
    @Mapping(expression = "java(entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : \"\")", target = "updatedAt")
    FunctionResponse toProto(FunctionEntity entity);

    CreateFunctionCommand toCreateCommand(CreateFunctionRequest request);

    UpdateFunctionCommand toUpdateCommand(UpdateFunctionRequest request);

    default ListFunctionsResponse toListProto(List<FunctionEntity> entities,
                                              long totalElements,
                                              int totalPages,
                                              int page,
                                              int size) {
        return ListFunctionsResponse.newBuilder()
                .addAllFunctions(entities.stream().map(this::toProto).toList())
                .setTotalElements(totalElements)
                .setTotalPages(totalPages)
                .setPage(page)
                .setSize(size)
                .build();
    }
}
