package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.project.CreateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.UpdateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.core.services.projects.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectMcpTools {

    private final ProjectService projectService;

    @McpTool(description = "List projects with optional name filter")
    public Page<ProjectEntity> listProjects(
            @McpToolParam(description = "Optional project name filter") String name) {
        return projectService.getListProject(name, PageRequest.of(0, 100));
    }

    @McpTool(description = "Get detailed information about a project")
    public ProjectEntity getProject(
            @McpToolParam(description = "Project ID") Long id) {
        return projectService.getProjectDetail(id);
    }

    @McpTool(description = "Create a new project. Example JSON: { \"name\": \"My Project\", \"description\": \"Stress test project\" }")
    public ProjectEntity createProject(
            @McpToolParam(description = "Creation command") CreateProjectCommand cmd) {
        return projectService.createProject(cmd);
    }

    @McpTool(description = "Update an existing project")
    public ProjectEntity updateProject(
            @McpToolParam(description = "Project ID") Long id,
            @McpToolParam(description = "Update command") UpdateProjectCommand cmd) {
        return projectService.updateProject(id, cmd);
    }

    @McpTool(description = "Delete a project")
    public void deleteProject(
            @McpToolParam(description = "Project ID") Long id) {
        projectService.deleteProject(id);
    }
}
