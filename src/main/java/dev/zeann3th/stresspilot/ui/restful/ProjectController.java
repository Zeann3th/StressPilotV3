package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.core.services.projects.ProjectService;
import dev.zeann3th.stresspilot.ui.restful.dtos.project.CreateProjectRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.project.ProjectResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.project.UpdateProjectRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import dev.zeann3th.stresspilot.ui.restful.mappers.ProjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@ResponseWrapper
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;

    @GetMapping
    public Page<ProjectResponseDTO> getListProjects(
            @RequestParam(value = "name", required = false) String name,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProjectEntity> resp = projectService.getListProject(name, pageable);
        return resp.map(projectMapper::toResponse);
    }

    @GetMapping("/{projectId}")
    public ProjectResponseDTO getProjectDetail(@PathVariable Long projectId) {
        ProjectEntity resp = projectService.getProjectDetail(projectId);
        return projectMapper.toResponse(resp);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponseDTO createProject(@Valid @RequestBody CreateProjectRequestDTO request) {
        var command = projectMapper.toCreateCommand(request);
        ProjectEntity resp = projectService.createProject(command);
        return projectMapper.toResponse(resp);
    }

    @PatchMapping("/{projectId}")
    public ProjectResponseDTO updateProject(@PathVariable Long projectId,
            @RequestBody UpdateProjectRequestDTO request) {
        var command = projectMapper.toUpdateCommand(request);
        ProjectEntity resp = projectService.updateProject(projectId, command);
        return projectMapper.toResponse(resp);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable("projectId") Long projectId) {
        projectService.deleteProject(projectId);
    }

    @GetMapping("/{projectId}/export")
    public ResponseEntity<ByteArrayResource> exportProject(@PathVariable Long projectId) {
        ByteArrayResource resource = projectService.exportProject(projectId);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"project_" + projectId + ".json\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(resource);
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ProjectResponseDTO importProject(@RequestPart("file") MultipartFile file) {
        ProjectEntity resp = projectService.importProject(file);
        return projectMapper.toResponse(resp);
    }
}
