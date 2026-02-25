package dev.zeann3th.stresspilot.core.services.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.*;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.ParserType;
import dev.zeann3th.stresspilot.core.domain.exception.BusinessExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.EndpointStore;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentVariableStore;
import dev.zeann3th.stresspilot.core.ports.store.ProjectStore;
import dev.zeann3th.stresspilot.core.services.EndpointService;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorService;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorServiceFactory;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.EndpointResponse;
import dev.zeann3th.stresspilot.core.services.parsers.ParserService;
import dev.zeann3th.stresspilot.core.services.parsers.ParserServiceFactory;
import dev.zeann3th.stresspilot.core.utils.EndpointUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EndpointServiceImpl implements EndpointService {
    private final EndpointStore endpointStore;
    private final ProjectStore projectStore;
    private final EnvironmentVariableStore envVarStore;
    private final ObjectMapper objectMapper;
    private final ParserServiceFactory parserServiceFactory;
    private final EndpointExecutorServiceFactory endpointExecutorServiceFactory;

    @Override
    public Page<EndpointEntity> getListEndpoint(Long projectId, String name, Pageable pageable) {
        return endpointStore.findAllByCondition(projectId, name, pageable);
    }

    @Override
    public EndpointEntity getEndpointDetail(Long endpointId) {
        return endpointStore.findById(endpointId)
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.ENDPOINT_NOT_FOUND));
    }

    @Override
    public EndpointEntity createEndpoint(CreateEndpointCommand command) {
        ProjectEntity project = projectStore.findById(command.getProjectId())
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.PROJECT_NOT_FOUND));

        EndpointEntity endpointEntity = buildEndpoint(project, command);
        return endpointStore.save(endpointEntity);
    }

    @Override
    public EndpointEntity updateEndpoint(UpdateEndpointCommand command) {
        EndpointEntity endpointEntity = endpointStore.findById(command.getId())
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.ENDPOINT_NOT_FOUND));

        Map<String, Object> updates = command.getUpdates();
        updates.remove("id");
        updates.remove("projectId");

        try {
            EndpointEntity updatedEntity = objectMapper.updateValue(endpointEntity, updates);
            return endpointStore.save(updatedEntity);
        } catch (JsonMappingException e) {
            throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST, Map.of(Constants.REASON, "Invalid request data: " + e.getMessage()));
        }
    }

    @Override
    public void deleteEndpoint(Long endpointId) {
        if (!endpointStore.findById(endpointId).isPresent()) {
            throw BusinessExceptionBuilder.exception(ErrorCode.ENDPOINT_NOT_FOUND);
        }
        endpointStore.deleteById(endpointId);
    }

    @Override
    public void uploadEndpoints(MultipartFile file, Long projectId) {
        ProjectEntity project = projectStore.findById(projectId)
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.PROJECT_NOT_FOUND));

        if (file == null || file.isEmpty()) {
            throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                    Map.of(Constants.REASON, "File is empty or null"));
        }

        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();

        ParserService parser;
        String type;
        if ("application/json".equals(contentType) || (originalFilename != null && originalFilename.endsWith(".json"))) {
            type = ParserType.POSTMAN.name();
        } else if (originalFilename != null && (originalFilename.endsWith(".proto") || originalFilename.endsWith(".pb"))) {
            type = ParserType.PROTO.name();
        } else {
            throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                    Map.of(Constants.REASON, "Unsupported file type: " + contentType));
        }

        parser = parserServiceFactory.getParser(type);

        try {
            String fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            List<EndpointEntity> parsedEntities = parser.parse(fileContent);

            parsedEntities.forEach(e -> e.setProject(project));

            endpointStore.saveAll(parsedEntities);
        } catch (Exception e) {
            throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                    Map.of(Constants.REASON, "Failed to parse or save endpoints: " + e.getMessage()));
        }
    }

    @Override
    public EndpointResponse runEndpoint(ExecuteEndpointCommand command) {
        EndpointEntity storedEntity = endpointStore.findById(command.getEndpointId())
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.ENDPOINT_NOT_FOUND));

        ProjectEntity projectEntity = storedEntity.getProject();

        Map<String, Object> environment = envVarStore
                .findAllByEnvironmentIdAndActiveTrue(projectEntity.getEnvironment().getId())
                .stream()
                .collect(Collectors.toMap(
                        EnvironmentVariableEntity::getKey,
                        EnvironmentVariableEntity::getValue,
                        (v1, v2) -> v2
                ));

        if (command.getVariables() != null) {
            environment.putAll(command.getVariables());
        }

        EndpointEntity effectiveEntity = EndpointUtils.merge(storedEntity, command);

        EndpointExecutorService endpointExecutorService = endpointExecutorServiceFactory.getExecutor(effectiveEntity.getType());

        long startTime = System.currentTimeMillis();
        try {
            EndpointResponse response = endpointExecutorService.execute(effectiveEntity, environment, null);
            EndpointUtils.evaluateSuccessCondition(effectiveEntity, response);
            return response;
        } catch (Exception e) {
            log.error("Error executing endpoint {}: {}", command.getEndpointId(), e.getMessage(), e);
            Map<String, Object> data = Map.of("error", e.getMessage());
            return EndpointResponse.builder()
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .success(false)
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .data(data)
                    .rawResponse(data.toString())
                    .build();
        }
    }

    @Override
    public EndpointResponse runAdhocEndpoint(ExecuteAdhocEndpointCommand command) {
        ProjectEntity projectEntity = projectStore.findById(command.getProjectId())
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.PROJECT_NOT_FOUND));

        Map<String, Object> environment = envVarStore
                .findAllByEnvironmentIdAndActiveTrue(projectEntity.getEnvironment().getId())
                .stream()
                .collect(Collectors.toMap(
                        EnvironmentVariableEntity::getKey,
                        EnvironmentVariableEntity::getValue,
                        (v1, v2) -> v2
                ));

        if (command.getVariables() != null) {
            environment.putAll(command.getVariables());
        }

        EndpointEntity adhocEntity = buildAdhocEndpoint(projectEntity, command);

        EndpointExecutorService endpointExecutorService = endpointExecutorServiceFactory.getExecutor(adhocEntity.getType());

        long startTime = System.currentTimeMillis();
        try {
            EndpointResponse response = endpointExecutorService.execute(adhocEntity, environment, null);
            EndpointUtils.evaluateSuccessCondition(adhocEntity, response);
            return response;
        } catch (Exception e) {
            log.error("Error executing ad-hoc endpoint: {}", e.getMessage(), e);
            Map<String, Object> data = Map.of("error", e.getMessage());
            return EndpointResponse.builder()
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .success(false)
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .data(data)
                    .rawResponse(data.toString())
                    .build();
        }
    }

    private EndpointEntity buildEndpoint(ProjectEntity project, CreateEndpointCommand command) {
        try {
            String bodyJson = null;
            Object rawBody = command.getBody();

            if (rawBody != null) {
                if (rawBody instanceof String str) {
                    bodyJson = str.isBlank() ? "{}" : str;
                } else {
                    bodyJson = objectMapper.writeValueAsString(rawBody);
                }
            }

            return EndpointEntity.builder()
                    .name(command.getName())
                    .description(command.getDescription())
                    .type(command.getType())
                    .url(command.getUrl())
                    .body(bodyJson)
                    .httpMethod(command.getHttpMethod())
                    .httpHeaders(command.getHttpHeaders() != null ? objectMapper.writeValueAsString(command.getHttpHeaders()) : null)
                    .httpParameters(command.getHttpParameters() != null ? objectMapper.writeValueAsString(command.getHttpParameters()) : null)
                    .grpcServiceName(command.getGrpcServiceName())
                    .grpcMethodName(command.getGrpcMethodName())
                    .grpcStubPath(command.getGrpcStubPath())
                    .project(project)
                    .successCondition(command.getSuccessCondition())
                    .build();
        } catch (JsonProcessingException e) {
            throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                    Map.of(Constants.REASON, "Failed to serialize endpoint data: " + e.getMessage()));
        }
    }

    private EndpointEntity buildAdhocEndpoint(ProjectEntity project, ExecuteAdhocEndpointCommand command) {
        try {
            String bodyJson = null;
            Object rawBody = command.getBody();
            if (rawBody != null) {
                if (rawBody instanceof String str) {
                    bodyJson = str.isBlank() ? "{}" : str;
                } else {
                    bodyJson = objectMapper.writeValueAsString(rawBody);
                }
            }

            String type = command.getType();

            return EndpointEntity.builder()
                    .name("Request_" + type + "_" + System.currentTimeMillis())
                    .description("Ad-hoc Endpoint Execution")
                    .type(type)
                    .url(command.getUrl())
                    .body(bodyJson)
                    .httpMethod(command.getHttpMethod())
                    .httpHeaders(command.getHttpHeaders() != null ? objectMapper.writeValueAsString(command.getHttpHeaders()) : null)
                    .httpParameters(command.getHttpParameters() != null ? objectMapper.writeValueAsString(command.getHttpParameters()) : null)
                    .grpcServiceName(command.getGrpcServiceName())
                    .grpcMethodName(command.getGrpcMethodName())
                    .grpcStubPath(command.getGrpcStubPath())
                    .project(project)
                    .successCondition(command.getSuccessCondition())
                    .build();

        } catch (JsonProcessingException e) {
            throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                    Map.of(Constants.REASON, "Failed to serialize ad-hoc endpoint data: " + e.getMessage()));
        }
    }
}
