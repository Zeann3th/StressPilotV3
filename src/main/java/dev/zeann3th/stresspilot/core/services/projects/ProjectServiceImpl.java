package dev.zeann3th.stresspilot.core.services.projects;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.commands.project.CreateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.ImportProjectCommand;
import dev.zeann3th.stresspilot.core.domain.commands.project.UpdateProjectCommand;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.*;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.enums.FlowType;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.*;
import dev.zeann3th.stresspilot.core.services.parsers.ProjectParser;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings({ "java:S3776", "java:S6541" })
public class ProjectServiceImpl implements ProjectService {

    private static final String DEFAULT_ENVIRONMENT_NAME = "Environment";

    private final ProjectStore projectStore;
    private final EnvironmentStore environmentStore;
    private final EnvironmentVariableStore envVarStore;
    private final EndpointStore endpointStore;
    private final FlowStore flowStore;
    private final FlowStepStore flowStepStore;
    private final ProjectParser projectParser;

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
        EnvironmentEntity env = environmentStore.save(EnvironmentEntity.builder()
                .name("Default")
                .build());
        ProjectEntity project = ProjectEntity.builder()
                .name(createProjectCommand.getName())
                .description(createProjectCommand.getDescription())
                .legacyEnvironmentId(env.getId())
                .activeEnvironment(env)
                .build();
        ProjectEntity savedProject = projectStore.save(project);
        env.setProject(savedProject);
        environmentStore.save(env);
        return savedProject;
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
    @Transactional(readOnly = true)
    public List<EnvironmentEntity> getProjectEnvironments(Long projectId) {
        if (!projectStore.existsById(projectId)) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0002);
        }
        return environmentStore.findAllByProjectId(projectId);
    }

    @Override
    @Transactional
    public EnvironmentEntity createProjectEnvironment(Long projectId, String name) {
        ProjectEntity project = projectStore.findById(projectId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));
        String environmentName = name == null || name.isBlank() ? DEFAULT_ENVIRONMENT_NAME : name.trim();
        return environmentStore.save(EnvironmentEntity.builder()
                .name(environmentName)
                .project(project)
                .build());
    }

    @Override
    @Transactional
    public ProjectEntity switchActiveEnvironment(Long projectId, Long environmentId) {
        ProjectEntity project = projectStore.findById(projectId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));
        EnvironmentEntity environment = environmentStore.findById(environmentId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0016,
                        Map.of(Constants.ID, environmentId)));
        if (environment.getProject() == null || !Objects.equals(environment.getProject().getId(), projectId)) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0016,
                    Map.of(Constants.ID, environmentId));
        }
        project.setActiveEnvironment(environment);
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

            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            ImportProjectCommand cmd = projectParser.unmarshal(content);
            return persistProject(cmd);
        } catch (Exception e) {
            log.error("Import failed", e);
            throw CommandExceptionBuilder.exception(ErrorCode.ER0011);
        }
    }

    @Override
    public ByteArrayResource exportProject(Long projectId) {
        try {
            ImportProjectCommand cmd = buildExportCommand(projectId);
            String yaml = projectParser.marshal(cmd);
            return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Export failed", e);
            throw CommandExceptionBuilder.exception(ErrorCode.ER0012);
        }
    }

    private ProjectEntity persistProject(ImportProjectCommand cmd) {
        EnvironmentEntity env = environmentStore.save(EnvironmentEntity.builder()
                .name("Default")
                .build());
        ProjectEntity project = projectStore.save(ProjectEntity.builder()
                .name(cmd.getName())
                .description(cmd.getDescription())
                .legacyEnvironmentId(env.getId())
                .activeEnvironment(env)
                .build());
        env.setProject(project);
        environmentStore.save(env);

        persistEnvironments(cmd, project, env);
        Map<String, EndpointEntity> endpointById = persistEndpoints(cmd.getEndpoints(), project);
        persistFlows(cmd.getFlows(), project, endpointById);

        return project;
    }

    private void persistEnvironments(ImportProjectCommand cmd, ProjectEntity project, EnvironmentEntity defaultEnv) {
        List<ImportProjectCommand.Environment> environments = cmd.getEnvironments();
        if (environments == null || environments.isEmpty()) {
            persistEnvironment(cmd.getEnvironment(), defaultEnv);
            return;
        }

        EnvironmentEntity active = defaultEnv;
        for (int i = 0; i < environments.size(); i++) {
            ImportProjectCommand.Environment envCommand = environments.get(i);
            EnvironmentEntity env = i == 0
                    ? defaultEnv
                    : environmentStore.save(EnvironmentEntity.builder()
                    .name(envCommand.getName() == null ? DEFAULT_ENVIRONMENT_NAME : envCommand.getName())
                    .project(project)
                    .build());
            env.setName(envCommand.getName() == null ? DEFAULT_ENVIRONMENT_NAME : envCommand.getName());
            env.setProject(project);
            environmentStore.save(env);
            persistEnvironment(envCommand.getVariables(), env);
            if (Boolean.TRUE.equals(envCommand.getActive())) {
                active = env;
            }
        }

        project.setActiveEnvironment(active);
        projectStore.save(project);
    }

    private void persistEnvironment(List<ImportProjectCommand.EnvVar> envVars, EnvironmentEntity env) {
        if (envVars == null || envVars.isEmpty()) return;

        List<EnvironmentVariableEntity> vars = new ArrayList<>();
        for (ImportProjectCommand.EnvVar v : envVars) {
            vars.add(EnvironmentVariableEntity.builder()
                    .environment(env)
                    .key(v.getName())
                    .value(v.getValue())
                    .active(v.getActive() == null || v.getActive())
                    .build());
        }
        envVarStore.saveAll(vars);
    }

    private Map<String, EndpointEntity> persistEndpoints(List<ImportProjectCommand.Endpoint> endpoints,
                                                          ProjectEntity project) {
        Map<String, EndpointEntity> endpointById = new HashMap<>();
        if (endpoints == null || endpoints.isEmpty()) return endpointById;

        for (ImportProjectCommand.Endpoint e : endpoints) {
            EndpointEntity saved = endpointStore.save(EndpointEntity.builder()
                    .project(project)
                    .name(e.getName())
                    .description(e.getDescription())
                    .type(e.getType() != null ? e.getType() : "HTTP")
                    .url(e.getUrl())
                    .httpMethod(e.getMethod())
                    .httpHeaders(writeMap(e.getHeaders()))
                    .httpParameters(writeMap(e.getParameters()))
                    .body(writeBody(e.getBody()))
                    .successCondition(e.getSuccessCondition())
                    .grpcServiceName(e.getGrpcServiceName())
                    .grpcMethodName(e.getGrpcMethodName())
                    .grpcStubPath(e.getGrpcStubPath())
                    .build());
            if (e.getId() != null) {
                endpointById.put(e.getId(), saved);
            }
        }
        return endpointById;
    }

    private void persistFlows(List<ImportProjectCommand.Flow> flows, ProjectEntity project,
                              Map<String, EndpointEntity> endpointById) {
        if (flows == null || flows.isEmpty()) return;

        Map<String, FlowEntity> flowByName = new LinkedHashMap<>();
        Map<String, Long> flowNameToId = new HashMap<>();
        for (ImportProjectCommand.Flow f : flows) {
            try {
                if (f.getSteps() == null || f.getSteps().isEmpty()) {
                    log.warn("Skipping imported flow '{}' because it has no steps", f.getName());
                    continue;
                }
                String flowType = f.getType() != null ? f.getType() : FlowType.DEFAULT.name();
                if (Arrays.stream(FlowType.values()).noneMatch(type -> type.name().equalsIgnoreCase(flowType))) {
                    log.warn("Skipping imported flow '{}' because flow type '{}' is unavailable", f.getName(), flowType);
                    continue;
                }
                FlowEntity flow = flowStore.save(FlowEntity.builder()
                        .project(project)
                        .name(f.getName())
                        .description(f.getDescription())
                        .type(flowType.toUpperCase())
                        .build());
                flowByName.put(f.getName(), flow);
                flowNameToId.put(f.getName(), flow.getId());
            } catch (RuntimeException ex) {
                log.warn("Skipping imported flow '{}': {}", f.getName(), ex.getMessage());
            }
        }

        for (ImportProjectCommand.Flow f : flows) {
            FlowEntity flow = flowByName.get(f.getName());
            if (flow == null) continue;
            List<ImportProjectCommand.Step> steps = f.getSteps() == null ? List.of() : f.getSteps();

            try {
                Map<String, String> stepNameToUuid = new HashMap<>();
                for (ImportProjectCommand.Step s : steps) {
                    if (s.getName() != null) {
                        stepNameToUuid.put(s.getName(), UUID.randomUUID().toString());
                    }
                }

                List<FlowStepEntity> stepEntities = new ArrayList<>();
                for (ImportProjectCommand.Step s : steps) {
                    String uuid = stepNameToUuid.getOrDefault(s.getName(), UUID.randomUUID().toString());

                    EndpointEntity endpointRef = s.getEndpoint() != null
                            ? endpointById.get(s.getEndpoint()) : null;
                    if (s.getEndpoint() != null && endpointRef == null) {
                        throw new IllegalArgumentException("endpoint reference not found: " + s.getEndpoint());
                    }

                    String nextIfTrue = s.getNextIfTrue() != null
                            ? stepNameToUuid.get(s.getNextIfTrue()) : null;
                    String nextIfFalse = s.getNextIfFalse() != null
                            ? stepNameToUuid.get(s.getNextIfFalse()) : null;

                    String condition = s.getCondition();
                    if (FlowStepType.SUBFLOW.name().equalsIgnoreCase(s.getType()) && condition != null
                            && flowNameToId.containsKey(condition)) {
                        condition = String.valueOf(flowNameToId.get(condition));
                    }

                    Map<String, Object> preProcess = remapLoopBody(s.getPreProcess(), stepNameToUuid);

                    stepEntities.add(FlowStepEntity.builder()
                            .id(uuid)
                            .flow(flow)
                            .type(s.getType())
                            .endpoint(endpointRef)
                            .preProcessor(writeMap(preProcess))
                            .postProcessor(writeMap(s.getPostProcess()))
                            .nextIfTrue(nextIfTrue)
                            .nextIfFalse(nextIfFalse)
                            .condition(condition)
                            .build());
                }
                if (!stepEntities.isEmpty())
                    flowStepStore.saveAll(stepEntities);
            } catch (RuntimeException ex) {
                log.warn("Skipping imported flow '{}': {}", f.getName(), ex.getMessage());
                flowStepStore.deleteAllByFlowId(flow.getId());
                flowStore.deleteById(flow.getId());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> remapLoopBody(Map<String, Object> preProcess, Map<String, String> stepNameToUuid) {
        if (preProcess == null || preProcess.isEmpty()) {
            return preProcess;
        }

        Map<String, Object> remapped = new LinkedHashMap<>(preProcess);
        Object loopObj = remapped.get("loop");
        if (!(loopObj instanceof Map<?, ?> loopMap)) {
            return remapped;
        }

        Map<String, Object> loop = new LinkedHashMap<>((Map<String, Object>) loopMap);
        Object body = loop.get("body");
        if (body != null) {
            String bodyId = body.toString();
            loop.put("body", stepNameToUuid.getOrDefault(bodyId, bodyId));
        }
        remapped.put("loop", loop);
        return remapped;
    }

    private ImportProjectCommand buildExportCommand(Long projectId) {
        ProjectEntity project = projectStore.findById(projectId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));

        List<EnvironmentEntity> environments = environmentStore.findAllByProjectId(projectId);
        List<EndpointEntity> endpoints = endpointStore.findAllByProjectId(projectId);
        List<FlowEntity> flows = flowStore.findAllByProjectId(projectId);

        Map<Long, String> endpointIdToUuid = new HashMap<>();
        List<ImportProjectCommand.Endpoint> epCommands = new ArrayList<>();
        for (EndpointEntity e : endpoints) {
            String uuid = UUID.randomUUID().toString();
            endpointIdToUuid.put(e.getId(), uuid);
            epCommands.add(ImportProjectCommand.Endpoint.builder()
                    .id(uuid)
                    .name(e.getName())
                    .description(e.getDescription())
                    .type(e.getType())
                    .url(e.getUrl())
                    .method(e.getHttpMethod())
                    .headers(readMap(e.getHttpHeaders()))
                    .parameters(readMap(e.getHttpParameters()))
                    .body(e.getBody())
                    .successCondition(e.getSuccessCondition())
                    .grpcServiceName(e.getGrpcServiceName())
                    .grpcMethodName(e.getGrpcMethodName())
                    .grpcStubPath(e.getGrpcStubPath())
                    .build());
        }

        Map<Long, String> flowIdToName = new HashMap<>();
        for (FlowEntity f : flows) {
            flowIdToName.put(f.getId(), f.getName());
        }

        List<ImportProjectCommand.Flow> flowCommands = new ArrayList<>();
        for (FlowEntity f : flows) {
            List<FlowStepEntity> steps = flowStepStore.findAllByFlowId(f.getId());
            steps.sort((a, b) -> {
                if ("START".equalsIgnoreCase(a.getType())) return -1;
                if ("START".equalsIgnoreCase(b.getType())) return 1;
                return a.getId().compareTo(b.getId());
            });

            List<ImportProjectCommand.Step> stepCommands = new ArrayList<>();
            for (FlowStepEntity s : steps) {
                String endpointRef = null;
                if (s.getEndpoint() != null) {
                    endpointRef = endpointIdToUuid.get(s.getEndpointId());
                }

                String condition = s.getCondition();
                if (FlowStepType.SUBFLOW.name().equalsIgnoreCase(s.getType()) && condition != null) {
                    try {
                        Long flowId = Long.parseLong(condition);
                        String flowName = flowIdToName.get(flowId);
                        if (flowName != null) {
                            condition = flowName;
                        }
                    } catch (NumberFormatException _) {
                        // already a name
                    }
                }

                stepCommands.add(ImportProjectCommand.Step.builder()
                        .name(s.getId())
                        .type(s.getType())
                        .endpoint(endpointRef)
                        .condition(condition)
                        .nextIfTrue(s.getNextIfTrue())
                        .nextIfFalse(s.getNextIfFalse())
                        .preProcess(readMap(s.getPreProcessor()))
                        .postProcess(readMap(s.getPostProcessor()))
                        .build());
            }

            flowCommands.add(new ImportProjectCommand.Flow(
                    f.getName(),
                    f.getDescription(),
                    f.getType(),
                    stepCommands));
        }

        return ImportProjectCommand.builder()
                .name(project.getName())
                .description(project.getDescription())
                .environments(environments.stream()
                        .map(env -> new ImportProjectCommand.Environment(
                                env.getName(),
                                Objects.equals(env.getId(), project.getActiveEnvironmentId()),
                                envVarStore.findAllByEnvironmentId(env.getId()).stream()
                                        .map(v -> new ImportProjectCommand.EnvVar(v.getKey(), v.getValue(), v.getActive()))
                                        .toList()))
                        .toList())
                .endpoints(epCommands)
                .flows(flowCommands)
                .build();
    }

    private String writeMap(Map<String, Object> map) {
        if (map == null || map.isEmpty())
            return null;
        return DataUtils.parseObjToJson(map);
    }

    private String writeBody(Object body) {
        if (body == null)
            return null;
        return DataUtils.parseObjToString(body);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank())
            return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception _) {
            return Collections.emptyMap();
        }
    }
}
