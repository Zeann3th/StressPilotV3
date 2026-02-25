package dev.zeann3th.stresspilot.ui.restful;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.commands.flow.ConfigureFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.UpdateFlowCommand;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.BusinessExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.FlowService;
import dev.zeann3th.stresspilot.ui.restful.dtos.flows.*;
import dev.zeann3th.stresspilot.ui.restful.mappers.FlowMapper;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/flows")
@RequiredArgsConstructor
public class FlowController {
    private final FlowService flowService;
    private final FlowMapper flowMapper;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Page<FlowResponseDTO>> getListFlow(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "name", required = false) String name,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        var entities = flowService.getListFlow(projectId, name, pageable);
        return ResponseEntity.ok(entities.map(flowMapper::toDTO));
    }

    @GetMapping("/{flowId}")
    public ResponseEntity<FlowResponseDTO> getFlowDetail(@PathVariable Long flowId) {
        var entity = flowService.getFlowDetail(flowId);
        return ResponseEntity.ok(flowMapper.toDTO(entity));
    }

    @PostMapping
    public ResponseEntity<FlowResponseDTO> createFlow(@Valid @RequestBody CreateFlowRequestDTO flowDTO) {
        var command = flowMapper.toCreateCommand(flowDTO);
        var entity = flowService.createFlow(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(flowMapper.toDTO(entity));
    }

    @PostMapping("/{flowId}/configuration")
    public ResponseEntity<List<FlowStepDTO>> configureFlow(@PathVariable Long flowId, @RequestBody List<FlowStepDTO> steps) {
        var command = ConfigureFlowCommand.builder()
                .flowId(flowId)
                .steps(flowMapper.toStepCommandList(steps))
                .build();
        var responses = flowService.configureFlow(command);
        return ResponseEntity.ok(flowMapper.toStepDTOList(responses));
    }

    @DeleteMapping("/{flowId}")
    public ResponseEntity<Void> deleteFlow(@PathVariable Long flowId) {
        flowService.deleteFlow(flowId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{flowId}")
    public ResponseEntity<FlowResponseDTO> updateFlow(@PathVariable Long flowId, @RequestBody Map<String, Object> flowDTO) {
        var command = UpdateFlowCommand.builder()
                .id(flowId)
                .updates(flowDTO)
                .build();
        var entity = flowService.updateFlow(command);
        return ResponseEntity.ok(flowMapper.toDTO(entity));
    }

    @PostMapping(value = "/{flowId}/execute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> runFlow(
            @PathVariable Long flowId,
            @Parameter(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
            @RequestPart("request") RunFlowRequestDTO runFlowRequestDTO,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        List<Map<String, Object>> credentials = parseCredentialsFile(file);
        var command = flowMapper.toRunCommand(flowId, runFlowRequestDTO, credentials);
        flowService.runFlow(command);
        return ResponseEntity.accepted().build();
    }

    private List<Map<String, Object>> parseCredentialsFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(file.getInputStream(), new TypeReference<>() {});
        } catch (IOException e) {
            throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST, Map.of("reason", "Invalid file format: " + e.getMessage()));
        }
    }
}
