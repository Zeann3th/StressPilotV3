package dev.zeann3th.stresspilot.ui.restful.mappers;

import dev.zeann3th.stresspilot.core.domain.commands.project.CreateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.UpdateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.infrastructure.configs.mappers.MapstructConfig;
import dev.zeann3th.stresspilot.ui.restful.dtos.project.CreateProjectRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.project.ProjectResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.project.UpdateProjectRequestDTO;
import org.mapstruct.*;

@Mapper(config = MapstructConfig.class)
public interface ProjectMapper {

    ProjectResponseDTO toResponse(ProjectEntity entity);

    CreateProjectCommand toCreateCommand(CreateProjectRequestDTO request);

    UpdateProjectCommand toUpdateCommand(UpdateProjectRequestDTO request);
}
