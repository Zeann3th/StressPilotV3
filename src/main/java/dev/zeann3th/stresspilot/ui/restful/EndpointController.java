package dev.zeann3th.stresspilot.ui.restful;

import dev.zeann3th.stresspilot.core.services.EndpointService;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoints.*;
import dev.zeann3th.stresspilot.ui.restful.mappers.EndpointMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/endpoints")
@RequiredArgsConstructor
public class EndpointController {
    private final EndpointService endpointService;
    private final EndpointMapper endpointMapper;

    @GetMapping
    public ResponseEntity<Page<EndpointDTO>> getListEndpoint(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "name", required = false) String name,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        var entities = endpointService.getListEndpoint(projectId, name, pageable);
        return ResponseEntity.ok(entities.map(endpointMapper::toDTO));
    }

    @GetMapping("/{endpointId}")
    public ResponseEntity<EndpointDTO> getEndpointDetail(@PathVariable("endpointId") Long endpointId) {
        var entity = endpointService.getEndpointDetail(endpointId);
        return ResponseEntity.ok(endpointMapper.toDTO(entity));
    }

    @PostMapping
    public ResponseEntity<EndpointDTO> createEndpoint(@Valid @RequestBody EndpointDTO createEndpointRequestDTO) {
        var command = endpointMapper.toCreateCommand(createEndpointRequestDTO);
        var entity = endpointService.createEndpoint(command);
        return ResponseEntity.ok(endpointMapper.toDTO(entity));
    }

    @PatchMapping("/{endpointId}")
    public ResponseEntity<EndpointDTO> updateEndpoint(
            @PathVariable Long endpointId,
            @RequestBody Map<String, Object> endpointDTO
    ) {
        var command = UpdateEndpointCommand.builder()
                .id(endpointId)
                .updates(endpointDTO)
                .build();
        var entity = endpointService.updateEndpoint(command);
        return ResponseEntity.ok(endpointMapper.toDTO(entity));
    }

    @DeleteMapping("/{endpointId}")
    public ResponseEntity<Void> deleteEndpoint(@PathVariable("endpointId") Long endpointId) {
        endpointService.deleteEndpoint(endpointId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadEndpoints(
            @RequestParam("file") MultipartFile file,
            @RequestParam("projectId") Long projectId) {
        endpointService.uploadEndpoints(file, projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{endpointId}/execute")
    public ResponseEntity<EndpointResponseDTO> executeEndpoint(
            @PathVariable Long endpointId,
            @RequestBody ExecuteEndpointRequestDTO requestBody
    ) {
        var command = endpointMapper.toExecuteCommand(endpointId, requestBody);
        var resp = endpointService.runEndpoint(command);
        return ResponseEntity.ok(endpointMapper.toResponseDTO(resp));
    }

    @PostMapping("/execute-adhoc")
    public ResponseEntity<EndpointResponseDTO> executeAdhocEndpoint(
            @RequestParam("projectId") Long projectId,
            @RequestBody ExecuteAdhocEndpointRequestDTO requestBody
    ) {
        var command = endpointMapper.toExecuteAdhocCommand(projectId, requestBody);
        var resp = endpointService.runAdhocEndpoint(command);
        return ResponseEntity.ok(endpointMapper.toResponseDTO(resp));
    }
}
