package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.project.CreateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.UpdateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.ui.restful.dtos.project.CreateProjectRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.project.ProjectResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.project.UpdateProjectRequestDTO;
import org.mapstruct.*;

@Mapper(config = dev.zeann3th.stresspilot.infrastructure.configs.MapstructConfig.class)
public interface ProjectMapper {

    @Mapping(source = "environment.id", target = "environmentId")
    ProjectResponseDTO toResponse(ProjectEntity entity);

    CreateProjectCommand toCreateCommand(CreateProjectRequestDTO request);

    UpdateProjectCommand toUpdateCommand(UpdateProjectRequestDTO request);
}
