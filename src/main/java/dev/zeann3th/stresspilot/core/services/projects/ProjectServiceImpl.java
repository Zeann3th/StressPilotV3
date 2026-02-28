package dev.zeann3th.stresspilot.core.services.projects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.commands.project.CreateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.UpdateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.ProjectImportExportCommand;
import dev.zeann3th.stresspilot.core.domain.entities.*;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({ "java:S3776", "java:S6541" })
public class ProjectServiceImpl implements ProjectService {

    private final ProjectStore projectStore;
    private final EnvironmentStore environmentStore;
    private final EnvironmentVariableStore envVarStore;
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
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));
    }

    @Override
    @Transactional
    public ProjectEntity createProject(CreateProjectCommand createProjectCommand) {
        EnvironmentEntity env = environmentStore.save(EnvironmentEntity.builder().build());
        ProjectEntity project = ProjectEntity.builder()
                .name(createProjectCommand.getName())
                .description(createProjectCommand.getDescription())
                .environment(env)
                .build();
        return projectStore.save(project);
    }

    @Override
    @Transactional
    public ProjectEntity updateProject(Long projectId, UpdateProjectCommand updateProjectCommand) {
        ProjectEntity project = projectStore.findById(projectId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));
        Optional.ofNullable(updateProjectCommand.getName()).ifPresent(project::setName);
        Optional.ofNullable(updateProjectCommand.getDescription()).ifPresent(project::setDescription);
        return projectStore.save(project);
    }

    @Override
    @Transactional
    public void deleteProject(Long projectId) {
        if (!projectStore.existsById(projectId)) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0002);
        }
        projectStore.deleteById(projectId);
    }

    @Override
    @Transactional
    public ProjectEntity importProject(MultipartFile file) {
        try {
            if (file == null || file.isEmpty())
                throw CommandExceptionBuilder.exception(ErrorCode.ER0014,
                        Map.of(Constants.REASON, "File is empty or null"));

            ProjectImportExportCommand payload = objectMapper.readValue(file.getInputStream(),
                    ProjectImportExportCommand.class);

            EnvironmentEntity env = environmentStore.save(EnvironmentEntity.builder().build());
            ProjectEntity project = projectStore.save(ProjectEntity.builder()
                    .name(payload.getName())
                    .description(payload.getDescription())
                    .environment(env)
                    .build());

            // Environment variables
            if (payload.getEnvironmentVariables() != null) {
                List<EnvironmentVariableEntity> vars = new java.util.ArrayList<>();
                for (ProjectImportExportCommand.EnvVarData v : payload.getEnvironmentVariables()) {
                    vars.add(EnvironmentVariableEntity.builder()
                            .environment(env)
                            .key(v.getKey())
                            .value(v.getValue())
                            .active(v.getActive() == null || v.getActive())
                            .build());
                }
                envVarStore.saveAll(vars);
            }

            // Endpoints
            Map<Long, Long> endpointIdMap = new HashMap<>();
            if (payload.getEndpoints() != null) {
                for (ProjectImportExportCommand.EndpointData e : payload.getEndpoints()) {
                    EndpointEntity saved = endpointStore.save(EndpointEntity.builder()
                            .project(project)
                            .name(e.getName())
                            .description(e.getDescription())
                            .type(e.getType())
                            .url(e.getUrl())
                            .httpMethod(e.getHttpMethod())
                            .httpHeaders(writeMap(e.getHttpHeaders()))
                            .httpParameters(writeMap(e.getHttpParameters()))
                            .body(writeBody(e.getBody()))
                            .grpcServiceName(e.getGrpcServiceName())
                            .grpcMethodName(e.getGrpcMethodName())
                            .grpcStubPath(e.getGrpcStubPath())
                            .build());
                    if (e.getOldId() != null)
                        endpointIdMap.put(e.getOldId(), saved.getId());
                }
            }

            // Flows + steps
            if (payload.getFlows() != null) {
                for (ProjectImportExportCommand.FlowData f : payload.getFlows()) {
                    FlowEntity flow = flowStore.save(FlowEntity.builder()
                            .project(project)
                            .name(f.getName())
                            .description(f.getDescription())
                            .build());

                    List<ProjectImportExportCommand.StepData> steps = f.getSteps() == null ? List.of() : f.getSteps();
                    Map<String, String> stepIdRemap = new HashMap<>();
                    for (ProjectImportExportCommand.StepData s : steps)
                        if (s.getId() != null)
                            stepIdRemap.put(s.getId(), UUID.randomUUID().toString());

                    List<FlowStepEntity> stepEntities = new ArrayList<>();
                    for (ProjectImportExportCommand.StepData s : steps) {
                        EndpointEntity endpointRef = null;
                        if (s.getOldEndpointId() != null) {
                            Long newId = endpointIdMap.get(s.getOldEndpointId());
                            endpointRef = newId != null ? endpointStore.findById(newId).orElse(null) : null;
                        }
                        stepEntities.add(FlowStepEntity.builder()
                                .id(stepIdRemap.getOrDefault(s.getId(), UUID.randomUUID().toString()))
                                .flow(flow)
                                .type(s.getType())
                                .endpoint(endpointRef)
                                .preProcessor(writeMap(s.getPreProcessor()))
                                .postProcessor(writeMap(s.getPostProcessor()))
                                .nextIfTrue(s.getNextIfTrue() != null ? stepIdRemap.get(s.getNextIfTrue()) : null)
                                .nextIfFalse(s.getNextIfFalse() != null ? stepIdRemap.get(s.getNextIfFalse()) : null)
                                .condition(s.getCondition())
                                .build());
                    }
                    if (!stepEntities.isEmpty())
                        flowStepStore.saveAll(stepEntities);
                }
            }

            return project;
        } catch (Exception e) {
            log.error("Import failed", e);
            throw CommandExceptionBuilder.exception(ErrorCode.ER0011);
        }
    }

    @Override
    public ByteArrayResource exportProject(Long projectId) {
        try {
            ProjectEntity project = projectStore.findById(projectId)
                    .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));
            Long envId = project.getEnvironment().getId();

            List<EnvironmentVariableEntity> envVars = envVarStore.findAllByEnvironmentId(envId);
            List<EndpointEntity> endpoints = endpointStore.findAllByProjectId(projectId);
            List<FlowEntity> flows = flowStore.findAllByProjectId(projectId);

            var payloadBuilder = ProjectImportExportCommand.builder()
                    .id(project.getId())
                    .name(project.getName())
                    .description(project.getDescription());

            payloadBuilder.environmentVariables(envVars.stream()
                    .map(v -> new ProjectImportExportCommand.EnvVarData(v.getId(), v.getKey(), v.getValue(),
                            v.getActive()))
                    .toList());

            payloadBuilder.endpoints(endpoints.stream()
                    .map(e -> ProjectImportExportCommand.EndpointData.builder()
                            .oldId(e.getId()).name(e.getName()).description(e.getDescription()).type(e.getType())
                            .url(e.getUrl()).httpMethod(e.getHttpMethod())
                            .httpHeaders(readMap(e.getHttpHeaders())).httpParameters(readMap(e.getHttpParameters()))
                            .body(e.getBody()).grpcServiceName(e.getGrpcServiceName())
                            .grpcMethodName(e.getGrpcMethodName()).grpcStubPath(e.getGrpcStubPath())
                            .build())
                    .toList());

            List<ProjectImportExportCommand.FlowData> flowDatas = new ArrayList<>();
            for (FlowEntity f : flows) {
                List<FlowStepEntity> steps = flowStepStore.findAllByFlowId(f.getId());
                steps.sort((a, b) -> {
                    if ("START".equalsIgnoreCase(a.getType()))
                        return -1;
                    if ("START".equalsIgnoreCase(b.getType()))
                        return 1;
                    return a.getId().compareTo(b.getId());
                });
                List<ProjectImportExportCommand.StepData> stepDatas = steps.stream()
                        .map(s -> ProjectImportExportCommand.StepData.builder()
                                .id(s.getId()).type(s.getType())
                                .oldEndpointId(s.getEndpoint() != null ? s.getEndpoint().getId() : null)
                                .preProcessor(readMap(s.getPreProcessor()))
                                .postProcessor(readMap(s.getPostProcessor()))
                                .nextIfTrue(s.getNextIfTrue()).nextIfFalse(s.getNextIfFalse())
                                .condition(s.getCondition())
                                .build())
                        .toList();
                flowDatas.add(
                        new ProjectImportExportCommand.FlowData(f.getId(), f.getName(), f.getDescription(), stepDatas));
            }
            payloadBuilder.flows(flowDatas);

            byte[] bytes = objectMapper.writeValueAsBytes(payloadBuilder.build());
            return new ByteArrayResource(bytes);
        } catch (Exception e) {
            log.error("Export failed", e);
            throw CommandExceptionBuilder.exception(ErrorCode.ER0012);
        }
    }

    private String writeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty())
            return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception _) {
            return null;
        }
    }

    private String writeBody(Object body) {
        if (body == null)
            return null;
        if (body instanceof String s)
            return s;
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception _) {
            return null;
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank())
            return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception _) {
            return Collections.emptyMap();
        }
    }
}
