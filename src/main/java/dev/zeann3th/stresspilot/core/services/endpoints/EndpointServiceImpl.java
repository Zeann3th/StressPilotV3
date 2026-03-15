package dev.zeann3th.stresspilot.core.services.endpoints;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.CreateEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteAdhocEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.EndpointStore;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentVariableStore;
import dev.zeann3th.stresspilot.core.ports.store.ProjectStore;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorFactory;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorUtils;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import dev.zeann3th.stresspilot.core.services.parsers.endpoints.ParserServiceFactory;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EndpointServiceImpl implements EndpointService {

    private final EndpointStore endpointStore;
    private final ProjectStore projectStore;
    private final EnvironmentVariableStore envVarStore;
    private final JsonMapper jsonMapper;
    private final ParserServiceFactory parserServiceFactory;
    private final EndpointExecutorFactory executorFactory;

    @Override
    public Page<EndpointEntity> getAllEndpoints(Long projectId, String name, Pageable pageable) {
        return endpointStore.findAllByCondition(projectId, name, pageable);
    }

    @Override
    public EndpointEntity getEndpointById(Long endpointId) {
        return endpointStore.findById(endpointId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0005));
    }

    @Override
    public EndpointEntity createEndpoint(CreateEndpointCommand cmd) {
        ProjectEntity project = projectStore.findById(cmd.getProjectId())
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));

        EndpointEntity entity = buildFromCommand(project, cmd);

        return endpointStore.save(entity);
    }

    @Override
    public EndpointEntity updateEndpoint(Long endpointId, Map<String, Object> patch) {
        EndpointEntity entity = endpointStore.findById(endpointId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0005));

        Map<String, Object> safe = patch.entrySet().stream()
                .filter(e -> !Set.of("id", "projectId", "project").contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        try {
            jsonMapper.updateValue(entity, safe);
            return endpointStore.save(entity);

        } catch (Exception e) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0019,
                    Map.of(Constants.REASON, e.getMessage()));
        }
    }

    @Override
    public void deleteEndpoint(Long endpointId) {
        if (endpointStore.findById(endpointId).isEmpty())
            throw CommandExceptionBuilder.exception(ErrorCode.ER0005);
        endpointStore.deleteById(endpointId);
    }

    @Override
    public void uploadEndpoints(MultipartFile file, Long projectId) {
        if (file == null || file.isEmpty())
            throw CommandExceptionBuilder.exception(ErrorCode.ER0014,
                    Map.of(Constants.REASON, "File is empty or null"));

        ProjectEntity project = projectStore.findById(projectId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));

        String filename = Objects.requireNonNull(file.getOriginalFilename(), "filename");
        String contentType = file.getContentType();
        String content;

        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception _) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0014,
                    Map.of(Constants.REASON, "Failed to read file content"));
        }

        List<EndpointEntity> parsed = parserServiceFactory
                .getParser(filename, contentType, content)
                .unmarshal(content);
        List<EndpointEntity> entities = new ArrayList<>();
        for (EndpointEntity e : parsed) {
            e.setProject(project);
            entities.add(e);
        }

        try {
            endpointStore.saveAll(entities);
        } catch (Exception e) {
            log.error("Database save failed: {}", e.getMessage(), e);
            throw CommandExceptionBuilder.exception(ErrorCode.ER0006,
                    Map.of(Constants.REASON, "Failed to save parsed endpoints: " + e.getMessage()));
        }
    }

    @Override
    public ExecuteEndpointResponse runEndpoint(Long endpointId, ExecuteEndpointCommand cmd) {
        EndpointEntity storedEndpoint = endpointStore.findById(endpointId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0005));

        ProjectEntity project = projectStore.findById(storedEndpoint.getProjectId())
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));

        Map<String, Object> environment = envVarStore
                .findAllByEnvironmentIdAndActiveTrue(project.getEnvironmentId())
                .stream()
                .collect(
                        Collectors.toMap(
                                EnvironmentVariableEntity::getKey,
                                EnvironmentVariableEntity::getValue,
                                (_, v2) -> v2));

        if (cmd.getVariables() != null)
            environment.putAll(cmd.getVariables());

        EndpointEntity effective = merge(project, storedEndpoint, cmd);
        long start = System.currentTimeMillis();
        try {
            ExecuteEndpointResponse resp = executorFactory.getExecutor(effective.getType())
                    .execute(effective, environment, new BaseExecutionContext());
            EndpointExecutorUtils.evaluateSuccessCondition(effective, resp);
            return resp;
        } catch (Exception e) {
            log.error("Error executing endpoint {}: {}", endpointId, e.getMessage(), e);
            Map<String, Object> errData = Map.of(Constants.ERROR, e.getMessage());
            return ExecuteEndpointResponse.builder()
                    .responseTimeMs(System.currentTimeMillis() - start)
                    .success(false)
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .data(errData)
                    .rawResponse(errData.toString())
                    .build();
        }
    }

    @Override
    public ExecuteEndpointResponse runAdhocEndpoint(Long projectId, ExecuteAdhocEndpointCommand cmd) {
        ProjectEntity project = projectStore.findById(projectId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));

        Map<String, Object> environment = envVarStore
                .findAllByEnvironmentIdAndActiveTrue(project.getEnvironmentId())
                .stream()
                .collect(Collectors.toMap(EnvironmentVariableEntity::getKey, EnvironmentVariableEntity::getValue,
                        (_, v2) -> v2));

        if (cmd.getVariables() != null)
            environment.putAll(cmd.getVariables());

        EndpointEntity adhoc = buildAdhoc(project, cmd);
        long start = System.currentTimeMillis();
        try {
            ExecuteEndpointResponse resp = executorFactory.getExecutor(adhoc.getType())
                    .execute(adhoc, environment, new BaseExecutionContext());
            EndpointExecutorUtils.evaluateSuccessCondition(adhoc, resp);
            return resp;
        } catch (Exception e) {
            log.error("Ad-hoc endpoint error: {}", e.getMessage(), e);
            Map<String, Object> errData = Map.of(Constants.ERROR, e.getMessage());
            return ExecuteEndpointResponse.builder()
                    .responseTimeMs(System.currentTimeMillis() - start)
                    .success(false)
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .data(errData).rawResponse(errData.toString())
                    .build();
        }
    }

    private EndpointEntity buildFromCommand(ProjectEntity project, CreateEndpointCommand cmd) {
        return EndpointEntity.builder()
                .project(project)
                .name(cmd.getName())
                .description(cmd.getDescription())
                .type(cmd.getType())
                .url(cmd.getUrl())
                .body(serializeBody(cmd.getBody()))
                .httpMethod(cmd.getHttpMethod())
                .httpHeaders(
                        cmd.getHttpHeaders() != null ? DataUtils.parseObjToJson(cmd.getHttpHeaders()) : null)
                .httpParameters(
                        cmd.getHttpParameters() != null ? DataUtils.parseObjToJson(cmd.getHttpParameters())
                                : null)
                .grpcServiceName(cmd.getGrpcServiceName())
                .grpcMethodName(cmd.getGrpcMethodName())
                .grpcStubPath(cmd.getGrpcStubPath())
                .successCondition(cmd.getSuccessCondition())
                .build();
    }

    private EndpointEntity buildAdhoc(ProjectEntity project, ExecuteAdhocEndpointCommand cmd) {
        return EndpointEntity.builder()
                .project(project)
                .name("Adhoc_" + cmd.getType() + "_" + System.currentTimeMillis())
                .type(cmd.getType())
                .url(cmd.getUrl())
                .body(serializeBody(cmd.getBody()))
                .httpMethod(cmd.getHttpMethod())
                .httpHeaders(
                        cmd.getHttpHeaders() != null ? DataUtils.parseObjToJson(cmd.getHttpHeaders()) : null)
                .httpParameters(
                        cmd.getHttpParameters() != null ? DataUtils.parseObjToJson(cmd.getHttpParameters())
                                : null)
                .grpcServiceName(cmd.getGrpcServiceName())
                .grpcMethodName(cmd.getGrpcMethodName())
                .grpcStubPath(cmd.getGrpcStubPath())
                .successCondition(cmd.getSuccessCondition())
                .build();
    }

    private String serializeBody(Object rawBody) {
        if (rawBody == null)
            return null;
        if (rawBody instanceof String s)
            return s.isBlank() ? "{}" : s;
        return DataUtils.parseObjToString(rawBody);
    }

    private EndpointEntity merge(ProjectEntity project, EndpointEntity endpoint, ExecuteEndpointCommand cmd) {
        if (cmd == null)
            return endpoint;

        EndpointEntity.EndpointEntityBuilder<?, ?> raw = EndpointEntity.builder()
                .project(project)
                .name(endpoint.getName())
                .description(endpoint.getDescription())
                .type(endpoint.getType())
                .url(endpoint.getUrl())
                .body(endpoint.getBody())
                .successCondition(endpoint.getSuccessCondition())
                .httpMethod(endpoint.getHttpMethod())
                .httpHeaders(endpoint.getHttpHeaders())
                .httpParameters(endpoint.getHttpParameters())
                .grpcServiceName(endpoint.getGrpcServiceName())
                .grpcMethodName(endpoint.getGrpcMethodName())
                .grpcStubPath(endpoint.getGrpcStubPath());

        if (DataUtils.hasText(cmd.getUrl()))
            raw.url(cmd.getUrl());

        if (DataUtils.hasText(cmd.getHttpMethod()))
            raw.httpMethod(cmd.getHttpMethod());

        if (cmd.getHttpHeaders() != null && !cmd.getHttpHeaders().isEmpty())
            raw.httpHeaders(DataUtils.parseObjToJson(cmd.getHttpHeaders()));

        if (cmd.getBody() != null)
            raw.body(DataUtils.parseObjToString(cmd.getBody()));

        if (cmd.getHttpParameters() != null && !cmd.getHttpParameters().isEmpty())
            raw.httpParameters(DataUtils.parseObjToJson(cmd.getHttpParameters()));

        if (DataUtils.hasText(cmd.getGrpcServiceName()))
            raw.grpcServiceName(cmd.getGrpcServiceName());

        if (DataUtils.hasText(cmd.getGrpcMethodName()))
            raw.grpcMethodName(cmd.getGrpcMethodName());

        if (DataUtils.hasText(cmd.getGrpcStubPath()))
            raw.grpcStubPath(cmd.getGrpcStubPath());

        if (DataUtils.hasText(cmd.getSuccessCondition()))
            raw.successCondition(cmd.getSuccessCondition());

        EndpointEntity merged = raw.build();
        merged.setId(endpoint.getId());
        return merged;
    }
}
