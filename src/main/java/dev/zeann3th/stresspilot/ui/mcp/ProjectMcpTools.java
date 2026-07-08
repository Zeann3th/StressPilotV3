package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.project.CreateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.UpdateProjectCommand;
import dev.zeann3th.stresspilot.core.services.projects.ProjectService;
import dev.zeann3th.stresspilot.ui.restful.dtos.environment.EnvironmentResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.project.ProjectResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.mappers.EnvironmentMapper;
import dev.zeann3th.stresspilot.ui.restful.mappers.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectMcpTools {

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    private final EnvironmentMapper environmentMapper;

    @McpTool(description = "List projects with optional name filter", generateOutputSchema = true)
    public Page<ProjectResponseDTO> listProjects(
            @McpToolParam(description = "Optional project name filter") String name) {
        return projectService.getListProject(name, PageRequest.of(0, 100))
                .map(projectMapper::toResponse);
    }

    @McpTool(description = "Get detailed information about a project", generateOutputSchema = true)
    public ProjectResponseDTO getProject(
            @McpToolParam(description = "Project ID") Long id) {
        return projectMapper.toResponse(projectService.getProjectDetail(id));
    }

    @McpTool(description = "Create a new project. Example JSON: { \"name\": \"My Project\", \"description\": \"Stress test project\" }", generateOutputSchema = true)
    public ProjectResponseDTO createProject(
            @McpToolParam(description = "Creation command") CreateProjectCommand cmd) {
        return projectMapper.toResponse(projectService.createProject(cmd));
    }

    @McpTool(description = "Update an existing project", generateOutputSchema = true)
    public ProjectResponseDTO updateProject(
            @McpToolParam(description = "Project ID") Long id,
            @McpToolParam(description = "Update command") UpdateProjectCommand cmd) {
        return projectMapper.toResponse(projectService.updateProject(id, cmd));
    }

    @McpTool(description = "List environments for a project", generateOutputSchema = true)
    public List<EnvironmentResponseDTO> listProjectEnvironments(
            @McpToolParam(description = "Project ID") Long projectId) {
        return projectService.getProjectEnvironments(projectId).stream()
                .map(environmentMapper::toResponse)
                .toList();
    }

    @McpTool(description = "Create an environment for a project", generateOutputSchema = true)
    public EnvironmentResponseDTO createProjectEnvironment(
            @McpToolParam(description = "Project ID") Long projectId,
            @McpToolParam(description = "Environment name") String name) {
        return environmentMapper.toResponse(projectService.createProjectEnvironment(projectId, name));
    }

    @McpTool(description = "Switch the active environment for a project", generateOutputSchema = true)
    public ProjectResponseDTO switchActiveProjectEnvironment(
            @McpToolParam(description = "Project ID") Long projectId,
            @McpToolParam(description = "Environment ID") Long environmentId) {
        return projectMapper.toResponse(projectService.switchActiveEnvironment(projectId, environmentId));
    }

    @McpTool(description = "Delete a project")
    public void deleteProject(
            @McpToolParam(description = "Project ID") Long id) {
        projectService.deleteProject(id);
    }
}
