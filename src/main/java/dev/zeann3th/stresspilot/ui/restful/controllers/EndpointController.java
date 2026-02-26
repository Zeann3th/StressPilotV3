package dev.zeann3th.stresspilot.ui.restful.controllers;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.services.endpoints.EndpointService;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.CreateEndpointRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.EndpointResponseDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.ExecuteAdhocEndpointRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.dtos.endpoint.ExecuteEndpointRequestDTO;
import dev.zeann3th.stresspilot.ui.restful.exception.ResponseWrapper;
import dev.zeann3th.stresspilot.ui.restful.mappers.EndpointMapper;
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

import java.util.Map;

@RestController
@RequestMapping("/api/v1/endpoints")
@RequiredArgsConstructor
@ResponseWrapper
public class EndpointController {

    private final EndpointService endpointService;
    private final EndpointMapper endpointMapper;

    @GetMapping
    public Page<EndpointResponseDTO> getListEndpoint(
            @RequestParam(value = "projectId", required = false) Long projectId,
            @RequestParam(value = "name", required = false) String name,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EndpointEntity> resp = endpointService.getAllEndpoints(projectId, name, pageable);
        return resp.map(endpointMapper::toResponse);
    }

    @GetMapping("/{endpointId}")
    public EndpointResponseDTO getEndpointDetail(@PathVariable("endpointId") Long endpointId) {
        EndpointEntity resp = endpointService.getEndpointById(endpointId);
        return endpointMapper.toResponse(resp);
    }

    @PostMapping
    public EndpointResponseDTO createEndpoint(@Valid @RequestBody CreateEndpointRequestDTO request) {
        EndpointEntity resp = endpointService.createEndpoint(endpointMapper.toCreateCommand(request));
        return endpointMapper.toResponse(resp);
    }

    @PatchMapping("/{endpointId}")
    public EndpointResponseDTO updateEndpoint(
            @PathVariable Long endpointId,
            @RequestBody Map<String, Object> endpointUpdateRequest) {
        EndpointEntity resp = endpointService.updateEndpoint(endpointId, endpointUpdateRequest);
        return endpointMapper.toResponse(resp);
    }

    @DeleteMapping("/{endpointId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEndpoint(@PathVariable("endpointId") Long endpointId) {
        endpointService.deleteEndpoint(endpointId);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadEndpoints(
            @RequestParam("file") MultipartFile file,
            @RequestParam("projectId") Long projectId) {
        endpointService.uploadEndpoints(file, projectId);
    }

    @PostMapping("/{endpointId}/execute")
    public ExecuteEndpointResponse executeEndpoint(
            @PathVariable Long endpointId,
            @RequestBody ExecuteEndpointRequestDTO requestBody) {
        ExecuteEndpointResponse resp = endpointService.runEndpoint(endpointId,
                endpointMapper.toExecuteCommand(requestBody));
        return resp;
    }

    @PostMapping("/execute-adhoc")
    public ExecuteEndpointResponse executeAdhocEndpoint(
            @RequestParam("projectId") Long projectId,
            @RequestBody ExecuteAdhocEndpointRequestDTO requestBody) {
        ExecuteEndpointResponse resp = endpointService.runAdhocEndpoint(projectId,
                endpointMapper.toAdhocCommand(requestBody));
        return resp;
    }
}
