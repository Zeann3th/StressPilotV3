package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.project.CreateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.UpdateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.core.services.projects.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectMcpTools {

    private final ProjectService projectService;

    @Tool(description = "List projects with optional name filter")
    public Page<ProjectEntity> listProjects(
            @ToolParam(description = "Optional project name filter") String name) {
        return projectService.getListProject(name, PageRequest.of(0, 100));
    }

    @Tool(description = "Get detailed information about a project")
    public ProjectEntity getProject(
            @ToolParam(description = "Project ID") Long id) {
        return projectService.getProjectDetail(id);
    }

    @Tool(description = "Create a new project. Example JSON: { \"name\": \"My Project\", \"description\": \"Stress test project\" }")
    public ProjectEntity createProject(
            @ToolParam(description = "Creation command") CreateProjectCommand cmd) {
        return projectService.createProject(cmd);
    }

    @Tool(description = "Update an existing project")
    public ProjectEntity updateProject(
            @ToolParam(description = "Project ID") Long id,
            @ToolParam(description = "Update command") UpdateProjectCommand cmd) {
        return projectService.updateProject(id, cmd);
    }

    @Tool(description = "Delete a project")
    public void deleteProject(
            @ToolParam(description = "Project ID") Long id) {
        projectService.deleteProject(id);
    }
}
