package dev.zeann3th.stresspilot.ui.grpc.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.project.CreateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.UpdateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.grpc.ui.*;
import dev.zeann3th.stresspilot.infrastructure.configs.mappers.MapstructProtoConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapstructProtoConfig.class)
public interface ProjectProtoMapper {

    @Mapping(source = "environment.id", target = "environmentId")
    ProjectResponse toProto(ProjectEntity entity);

    CreateProjectCommand toCreateCommand(CreateProjectRequest request);

    UpdateProjectCommand toUpdateCommand(UpdateProjectRequest request);

    default ListProjectsResponse toListProto(List<ProjectEntity> entities,
            long totalElements,
            int totalPages,
            int page,
            int size) {
        return ListProjectsResponse.newBuilder()
                .addAllProjects(entities.stream().map(this::toProto).toList())
                .setTotalElements(totalElements)
                .setTotalPages(totalPages)
                .setPage(page)
                .setSize(size)
                .build();
    }
}
