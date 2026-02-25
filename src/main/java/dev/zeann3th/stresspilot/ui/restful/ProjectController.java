package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.services.ProjectService;
import dev.zeann3th.stresspilot.ui.restful.dtos.projects.CreateProjectRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.projects.ProjectResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.projects.UpdateProjectRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.mappers.ProjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;
    private final ProjectMapper projectMapper;

    @GetMapping
    public ResponseEntity<Page<ProjectResponseDTO>> getListProjects(
            @RequestParam(value = "name", required = false) String name,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        var entities = projectService.getListProject(name, pageable);
        return ResponseEntity.ok(entities.map(projectMapper::toDTO));
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponseDTO> getProjectDetail(@PathVariable Long projectId) {
        var entity = projectService.getProjectDetail(projectId);
        return ResponseEntity.ok(projectMapper.toDTO(entity));
    }

    @PostMapping
    public ResponseEntity<ProjectResponseDTO> createProject(@Valid @RequestBody CreateProjectRequestDTO projectRequestDTO) {
        var command = projectMapper.toCreateCommand(projectRequestDTO);
        var entity = projectService.createProject(command);
        return ResponseEntity.status(201).body(projectMapper.toDTO(entity));
    }

    @PatchMapping("/{projectId}")
    public ResponseEntity<ProjectResponseDTO> updateProject(@PathVariable Long projectId,
                                                            @RequestBody UpdateProjectRequestDTO projectRequestDTO) {
        var command = projectMapper.toUpdateCommand(projectId, projectRequestDTO);
        var entity = projectService.updateProject(command);
        return ResponseEntity.ok(projectMapper.toDTO(entity));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(@PathVariable("projectId") Long projectId) {
        projectService.deleteProject(projectId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/export")
    public ResponseEntity<ByteArrayResource> exportProject(@PathVariable Long projectId) {
        return projectService.exportProject(projectId);
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    public ResponseEntity<ProjectResponseDTO> importProject(@RequestPart("file") MultipartFile file) {
        var resp = projectService.importProject(file);
        if (resp.getBody() != null) {
            return ResponseEntity.status(resp.getStatusCode()).body(projectMapper.toDTO(resp.getBody()));
        }
        return ResponseEntity.status(resp.getStatusCode()).build();
    }
}
