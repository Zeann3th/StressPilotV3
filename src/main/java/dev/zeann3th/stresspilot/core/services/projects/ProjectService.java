package dev.zeann3th.stresspilot.core.services.projects;

import dev.zeann3th.stresspilot.core.domain.commands.project.CreateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.UpdateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentEntity;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface ProjectService {
    Page<ProjectEntity> getListProject(String name, Pageable pageable);

    ProjectEntity getProjectDetail(Long projectId);

    ProjectEntity createProject(CreateProjectCommand createProjectCommand);

    ProjectEntity updateProject(Long projectId, UpdateProjectCommand updateProjectCommand);

    java.util.List<EnvironmentEntity> getProjectEnvironments(Long projectId);

    EnvironmentEntity createProjectEnvironment(Long projectId, String name);

    ProjectEntity switchActiveEnvironment(Long projectId, Long environmentId);

    void deleteProject(Long projectId);

    ProjectEntity importProject(MultipartFile file);

    ByteArrayResource exportProject(Long projectId);
}
