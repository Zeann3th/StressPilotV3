package dev.zeann3th.stresspilot.ui.restful.controllers;

import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.services.flows.FlowService;
import dev.zeann3th.stresspilot.ui.restful.dtos.flow.CreateFlowRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.flow.FlowResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.flow.FlowStepRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.flow.FlowStepResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.flow.RunFlowRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/flows")
@RequiredArgsConstructor
@ResponseWrapper
public class FlowController {

    private final FlowService flowService;
    private final FlowMapper flowMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public Page<FlowResponseDTO> getListFlow(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "name", required = false) String name,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<FlowEntity> resp = flowService.getListFlow(projectId, name, pageable);
        return resp.map(flowMapper::toResponse);
    }

    @GetMapping("/{flowId}")
    public FlowResponseDTO getFlowDetail(@PathVariable Long flowId) {
        FlowEntity resp = flowService.getFlowDetail(flowId);
        return flowMapper.toResponse(resp);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlowResponseDTO createFlow(@Valid @RequestBody CreateFlowRequestDTO request) {
        FlowEntity resp = flowService.createFlow(flowMapper.toCreateCommand(request));
        return flowMapper.toResponse(resp);
    }

    @PostMapping("/{flowId}/configuration")
    public List<FlowStepResponseDTO> configureFlow(@PathVariable Long flowId,
            @RequestBody List<FlowStepRequestDTO> steps) {
        var configuredSteps = flowService.configureFlow(flowId, flowMapper.toStepCommands(steps));
        return configuredSteps.getSteps().stream().map(flowMapper::toStepResponse).toList();
    }

    @DeleteMapping("/{flowId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFlow(@PathVariable Long flowId) {
        flowService.deleteFlow(flowId);
    }

    @PatchMapping("/{flowId}")
    public FlowResponseDTO updateFlow(@PathVariable Long flowId,
            @RequestBody Map<String, Object> flowDTO) {
        FlowEntity resp = flowService.updateFlow(flowId, flowDTO);
        return flowMapper.toResponse(resp);
    }

    @PostMapping(value = "/{flowId}/execute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void runFlow(
            @PathVariable Long flowId,
            @Parameter(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)) @RequestPart("request") RunFlowRequestDTO runFlowRequestDTO,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        List<Map<String, Object>> credentials = parseCredentialsFile(file);
        dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand cmd = flowMapper
                .toRunCommand(runFlowRequestDTO);
        cmd.setCredentials(credentials);
        flowService.runFlow(flowId, cmd);
    }

    private List<Map<String, Object>> parseCredentialsFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(file.getInputStream(), new TypeReference<>() {
            });
        } catch (IOException _) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0014);
        }
    }
}
