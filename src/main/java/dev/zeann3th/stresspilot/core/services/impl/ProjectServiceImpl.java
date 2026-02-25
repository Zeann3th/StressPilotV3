package dev.zeann3th.stresspilot.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.commands.project.CreateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.UpdateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.ProjectImportExport;
import dev.zeann3th.stresspilot.core.domain.entities.*;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.BusinessExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.*;
import dev.zeann3th.stresspilot.core.services.ProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {
    private final ProjectStore projectStore;
    private final EnvironmentStore environmentStore;
    private final EnvironmentVariableStore environmentVariableStore;
    private final EndpointStore endpointStore;
    private final FlowStore flowStore;
    private final FlowStepStore flowStepStore;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public Page<ProjectEntity> getListProject(String name, Pageable pageable) {
        return projectStore.findAllByCondition(name, pageable);
    }

    @Override
    public ProjectEntity getProjectDetail(Long projectId) {
        return projectStore.findById(projectId)
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.PROJECT_NOT_FOUND));
    }

    @Override
    @Transactional
    public ProjectEntity createProject(CreateProjectCommand command) {
        EnvironmentEntity environmentEntity = EnvironmentEntity.builder().build();
        EnvironmentEntity savedEnvironment = environmentStore.save(environmentEntity);

        ProjectEntity project = ProjectEntity.builder()
                .name(command.getName())
                .description(command.getDescription())
                .environment(savedEnvironment)
                .build();

        return projectStore.save(project);
    }

    @Override
    @Transactional
    public ProjectEntity updateProject(UpdateProjectCommand command) {
        ProjectEntity project = projectStore.findById(command.getId())
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.PROJECT_NOT_FOUND));

        Optional.ofNullable(command.getName()).ifPresent(project::setName);
        Optional.ofNullable(command.getDescription()).ifPresent(project::setDescription);

        return projectStore.save(project);
    }

    @Override
    @Transactional
    public void deleteProject(Long projectId) {
        ProjectEntity project = projectStore.findById(projectId)
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.PROJECT_NOT_FOUND));

        Long environmentId = project.getEnvironment().getId();

        flowStore.deleteAllByProjectId(projectId);
        endpointStore.deleteAllByProjectId(projectId);
        projectStore.deleteById(projectId);
        environmentVariableStore.deleteAllByEnvironmentId(environmentId);
        environmentStore.deleteById(environmentId);
    }

    @Override
    @Transactional
    public ResponseEntity<ProjectEntity> importProject(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) return ResponseEntity.badRequest().build();
            ProjectImportExport payload = objectMapper.readValue(file.getInputStream(), ProjectImportExport.class);

            EnvironmentEntity savedEnv = environmentStore.save(EnvironmentEntity.builder().build());

            ProjectEntity project = ProjectEntity.builder()
                    .name(payload.getName())
                    .description(payload.getDescription())
                    .environment(savedEnv)
                    .build();
            ProjectEntity savedProject = projectStore.save(project);

            if (payload.getEnvironmentVariables() != null) {
                List<EnvironmentVariableEntity> envToSave = payload.getEnvironmentVariables().stream().map(v ->
                        EnvironmentVariableEntity.builder()
                                .environment(savedEnv)
                                .key(v.getKey())
                                .value(v.getValue())
                                .active(v.getActive() == null || v.getActive())
                                .build()
                ).toList();
                environmentVariableStore.saveAll(envToSave);
            }

            Map<Long, EndpointEntity> endpointMapping = new HashMap<>();
            if (payload.getEndpoints() != null) {
                for (ProjectImportExport.Endpoint dto : payload.getEndpoints()) {
                    EndpointEntity endpoint = EndpointEntity.builder()
                            .project(savedProject)
                            .name(dto.getName())
                            .description(dto.getDescription())
                            .type(dto.getType())
                            .url(dto.getUrl())
                            .httpMethod(dto.getHttpMethod())
                            .httpHeaders(writeMapToJson(dto.getHttpHeaders()))
                            .httpParameters(writeMapToJson(dto.getHttpParameters()))
                            .body(writeBodyToJson(dto.getBody()))
                            .grpcServiceName(dto.getGrpcServiceName())
                            .grpcMethodName(dto.getGrpcMethodName())
                            .grpcStubPath(dto.getGrpcStubPath())
                            .successCondition(dto.getSuccessCondition())
                            .build();
                    EndpointEntity savedEndpoint = endpointStore.save(endpoint);
                    if (dto.getId() != null) {
                        endpointMapping.put(dto.getId(), savedEndpoint);
                    }
                }
            }

            if (payload.getFlows() != null) {
                for (ProjectImportExport.Flow flow : payload.getFlows()) {
                    FlowEntity flowEntity = FlowEntity.builder()
                            .project(savedProject)
                            .name(flow.getName())
                            .description(flow.getDescription())
                            .build();
                    FlowEntity savedFlow = flowStore.save(flowEntity);

                    List<ProjectImportExport.FlowStep> steps = flow.getSteps() == null ? List.of() : flow.getSteps();
                    Map<String, String> idMap = new HashMap<>();
                    for (ProjectImportExport.FlowStep s : steps)
                        if (s.getId() != null) idMap.put(s.getId(), UUID.randomUUID().toString());

                    List<FlowStepEntity> entities = new ArrayList<>();
                    for (ProjectImportExport.FlowStep s : steps) {
                        try {
                            EndpointEntity endpoint = null;
                            if (s.getEndpointId() != null) {
                                endpoint = endpointMapping.get(s.getEndpointId());
                            }

                            FlowStepEntity fe = FlowStepEntity.builder()
                                    .id(idMap.getOrDefault(s.getId(), UUID.randomUUID().toString()))
                                    .flow(savedFlow)
                                    .type(s.getType())
                                    .endpoint(endpoint)
                                    .preProcessor(writeMapToJson(s.getPreProcessor()))
                                    .postProcessor(writeMapToJson(s.getPostProcessor()))
                                    .nextIfTrue(s.getNextIfTrue() != null ? idMap.get(s.getNextIfTrue()) : null)
                                    .nextIfFalse(s.getNextIfFalse() != null ? idMap.get(s.getNextIfFalse()) : null)
                                    .condition(s.getCondition())
                                    .build();
                            entities.add(fe);
                        } catch (Exception e) {
                            log.warn("Failed to convert step processors or find endpoint: {}", e.getMessage());
                        }
                    }
                    if (!entities.isEmpty()) flowStepStore.saveAll(entities);
                }
            }

            return ResponseEntity.status(201).body(savedProject);
        } catch (Exception e) {
            log.error("Import failed", e);
            throw BusinessExceptionBuilder.exception(ErrorCode.PROJECT_IMPORT_ERROR);
        }
    }

    @Override
    public ResponseEntity<ByteArrayResource> exportProject(Long projectId) {
        try {
            ProjectEntity project = projectStore.findById(projectId)
                    .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.PROJECT_NOT_FOUND));

            List<EnvironmentVariableEntity> envVars = environmentVariableStore.findAllByEnvironmentId(project.getEnvironment().getId());
            List<ProjectImportExport.EnvironmentVariable> envVarDTOs = envVars.stream()
                    .map(ev -> ProjectImportExport.EnvironmentVariable.builder()
                            .id(ev.getId())
                            .environmentId(ev.getEnvironment().getId())
                            .key(ev.getKey())
                            .value(ev.getValue())
                            .active(ev.getActive())
                            .build())
                    .toList();

            List<EndpointEntity> endpoints = endpointStore.findAllByProjectId(projectId);

            List<ProjectImportExport.Endpoint> endpointDTOs = endpoints.stream()
                    .map(e -> ProjectImportExport.Endpoint.builder()
                            .id(e.getId())
                            .projectId(e.getProject().getId())
                            .name(e.getName())
                            .description(e.getDescription())
                            .type(e.getType())
                            .url(e.getUrl())
                            .httpMethod(e.getHttpMethod())
                            .httpHeaders(readJsonToMap(e.getHttpHeaders()))
                            .httpParameters(readJsonToMap(e.getHttpParameters()))
                            .body(e.getBody())
                            .grpcServiceName(e.getGrpcServiceName())
                            .grpcMethodName(e.getGrpcMethodName())
                            .grpcStubPath(e.getGrpcStubPath())
                            .successCondition(e.getSuccessCondition())
                            .build())
                    .toList();

            List<FlowEntity> flows = flowStore.findAllByProjectId(projectId);

            List<ProjectImportExport.Flow> flowDTOs = new ArrayList<>();
            for (FlowEntity f : flows) {
                ProjectImportExport.Flow fr = ProjectImportExport.Flow.builder()
                        .id(f.getId())
                        .projectId(f.getProject().getId())
                        .name(f.getName())
                        .description(f.getDescription())
                        .createdAt(f.getCreatedAt())
                        .updatedAt(f.getUpdatedAt())
                        .build();

                List<FlowStepEntity> steps = flowStepStore.findAllByFlowId(f.getId());
                steps.sort((a, b) -> {
                    if ("START".equalsIgnoreCase(a.getType())) return -1;
                    if ("START".equalsIgnoreCase(b.getType())) return 1;
                    return a.getId().compareTo(b.getId());
                });

                List<ProjectImportExport.FlowStep> stepDTOs = steps.stream().map(s -> {
                    Map<String, Object> pre = readJsonToMap(s.getPreProcessor());
                    Map<String, Object> post = readJsonToMap(s.getPostProcessor());
                    return ProjectImportExport.FlowStep.builder()
                            .id(s.getId())
                            .type(s.getType())
                            .endpointId(s.getEndpoint() != null ? s.getEndpoint().getId() : null)
                            .preProcessor(pre)
                            .postProcessor(post)
                            .nextIfTrue(s.getNextIfTrue())
                            .nextIfFalse(s.getNextIfFalse())
                            .condition(s.getCondition())
                            .build();
                }).toList();

                fr.setSteps(stepDTOs);
                flowDTOs.add(fr);
            }

            ProjectImportExport dto = ProjectImportExport.builder()
                    .id(project.getId())
                    .name(project.getName())
                    .description(project.getDescription())
                    .environmentVariables(envVarDTOs)
                    .endpoints(endpointDTOs)
                    .flows(flowDTOs)
                    .build();

            byte[] bytes = objectMapper.writeValueAsBytes(dto);
            var resource = new ByteArrayResource(bytes);
            String filename = String.format("project-%d.json", projectId);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", filename);
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(bytes.length)
                    .body(resource);
        } catch (Exception e) {
            log.error("Export failed", e);
            throw BusinessExceptionBuilder.exception(ErrorCode.PROJECT_EXPORT_ERROR);
        }
    }

    private Map<String, Object> readJsonToMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception _) {
            log.warn("Failed to parse JSON string: {}", json);
            return Collections.emptyMap();
        }
    }

    private String writeMapToJson(Map<String, Object> map) {
        if (map == null) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception _) {
            return null;
        }
    }

    private String writeBodyToJson(Object body) {
        if (body == null) return null;
        if (body instanceof String str) return str;
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception _) {
            return null;
        }
    }
}
