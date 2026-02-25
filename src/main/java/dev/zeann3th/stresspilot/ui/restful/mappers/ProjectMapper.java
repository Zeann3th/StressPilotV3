package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.project.CreateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.UpdateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.infrastructure.configs.MapstructConfig;
import dev.zeann3th.stresspilot.ui.restful.dtos.projects.CreateProjectRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.projects.ProjectResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.projects.UpdateProjectRequestDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapstructConfig.class)
public interface ProjectMapper {
    @Mapping(target = "environmentId", source = "environment.id")
    ProjectResponseDTO toDTO(ProjectEntity entity);

    List<ProjectResponseDTO> toDTOList(List<ProjectEntity> entityList);

    CreateProjectCommand toCreateCommand(CreateProjectRequestDTO dto);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "dto.name")
    @Mapping(target = "description", source = "dto.description")
    UpdateProjectCommand toUpdateCommand(Long id, UpdateProjectRequestDTO dto);
}
