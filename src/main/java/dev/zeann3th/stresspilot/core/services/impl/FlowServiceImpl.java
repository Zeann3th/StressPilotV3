package dev.zeann3th.stresspilot.core.services.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.commands.flow.ConfigureFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.CreateFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.UpdateFlowCommand;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.*;
import dev.zeann3th.stresspilot.core.domain.enums.ConfigKey;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.domain.exception.BusinessException;
import dev.zeann3th.stresspilot.core.domain.exception.BusinessExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.*;
import dev.zeann3th.stresspilot.core.services.ConfigService;
import dev.zeann3th.stresspilot.core.services.FlowService;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import dev.zeann3th.stresspilot.core.services.executors.EndpointExecutorServiceFactory;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.EndpointResponse;
import dev.zeann3th.stresspilot.core.utils.EndpointUtils;
import dev.zeann3th.stresspilot.core.utils.FlowThreadContext;
import dev.zeann3th.stresspilot.core.utils.FlowUtils;
import dev.zeann3th.stresspilot.core.utils.InMemoryCookieJar;
import dev.zeann3th.stresspilot.core.domain.commands.flow.FlowStepCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.FlowStepResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j(topic = "[Flow Service]")
@Service
@RequiredArgsConstructor
public class FlowServiceImpl implements FlowService {

    private final FlowStore flowStore;
    private final FlowStepStore flowStepStore;
    private final ProjectStore projectStore;
    private final EnvironmentVariableStore envVarStore;
    private final EndpointStore endpointStore;
    private final RunStore runStore;
    private final EndpointExecutorServiceFactory endpointExecutorServiceFactory;
    private final ObjectMapper objectMapper;
    private final ConfigService configService;
    private final RequestLogService requestLogService;

    @Override
    public Page<FlowEntity> getListFlow(Long projectId, String name, Pageable pageable) {
        return flowStore.findAllByCondition(projectId, name, pageable);
    }

    @Override
    public FlowEntity getFlowDetail(Long flowId) {
        return flowStore.findById(flowId)
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.FLOW_NOT_FOUND));
    }

    @Override
    @Transactional
    public FlowEntity createFlow(CreateFlowCommand command) {
        ProjectEntity project = projectStore.findById(command.getProjectId())
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.PROJECT_NOT_FOUND));

        FlowEntity flowEntity = FlowEntity.builder()
                .project(project)
                .name(command.getName())
                .description(command.getDescription())
                .build();
        return flowStore.save(flowEntity);
    }

    @Override
    @Transactional
    public void deleteFlow(Long flowId) {
        if (!flowStore.findById(flowId).isPresent())
            throw BusinessExceptionBuilder.exception(ErrorCode.FLOW_NOT_FOUND);

        flowStore.deleteById(flowId);
        flowStepStore.deleteAllByFlowId(flowId);
    }

    @Override
    @Transactional
    public FlowEntity updateFlow(UpdateFlowCommand command) {
        FlowEntity flowEntity = flowStore.findById(command.getId())
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.FLOW_NOT_FOUND));

        Map<String, Object> updates = command.getUpdates();
        updates.remove("id");
        updates.remove("projectId");

        try {
            objectMapper.updateValue(flowEntity, updates);
            return flowStore.save(flowEntity);
        } catch (Exception e) {
            throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                    Map.of(Constants.REASON, "Invalid data format: " + e.getMessage()));
        }
    }

    @Override
    @Transactional
    public List<FlowStepResponse> configureFlow(ConfigureFlowCommand command) {
        Long flowId = command.getFlowId();
        FlowEntity flow = flowStore.findById(flowId)
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.FLOW_NOT_FOUND));

        List<FlowStepCommand> steps = command.getSteps();
        FlowUtils.validateStartStep(steps);
        flowStepStore.deleteAllByFlowId(flowId);

        Map<String, String> stepIdMap = steps.stream()
                .collect(Collectors.toMap(FlowStepCommand::getId, _ -> UUID.randomUUID().toString()));

        for (FlowStepCommand dto : steps)
            validateStep(dto, stepIdMap);

        String allowInfinite = configService.getValue(ConfigKey.FLOW_ALLOW_INFINITE.name()).orElse("false");
        if (!Boolean.parseBoolean(allowInfinite)) {
            FlowUtils.detectInfiniteLoop(steps, stepIdMap);
        }

        List<FlowStepEntity> entities = new ArrayList<>();
        for (FlowStepCommand dto : steps) {
            try {
                EndpointEntity endpoint = null;
                if (dto.getEndpointId() != null) {
                    endpoint = endpointStore.findById(dto.getEndpointId())
                            .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.ENDPOINT_NOT_FOUND, Map.of("stepId", dto.getId())));
                }

                FlowStepEntity entity = FlowStepEntity.builder()
                        .id(stepIdMap.get(dto.getId()))
                        .flow(flow)
                        .type(dto.getType())
                        .endpoint(endpoint)
                        .preProcessor(dto.getPreProcessor() != null ? objectMapper.writeValueAsString(dto.getPreProcessor()) : null)
                        .postProcessor(dto.getPostProcessor() != null ? objectMapper.writeValueAsString(dto.getPostProcessor()) : null)
                        .nextIfTrue(dto.getNextIfTrue() != null ? stepIdMap.get(dto.getNextIfTrue()) : null)
                        .nextIfFalse(dto.getNextIfFalse() != null ? stepIdMap.get(dto.getNextIfFalse()) : null)
                        .condition(dto.getCondition())
                        .build();
                entities.add(entity);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception _) {
                throw BusinessExceptionBuilder.exception(ErrorCode.FLOW_CONFIGURATION_ERROR);
            }
        }

        flowStepStore.saveAll(entities);
        FlowUtils.sortSteps(entities);

        return entities.stream()
                .map(entity -> FlowStepResponse.builder()
                        .id(entity.getId())
                        .type(entity.getType())
                        .endpointId(entity.getEndpoint() != null ? entity.getEndpoint().getId() : null)
                        .preProcessor(parseProcessor(entity.getPreProcessor()))
                        .postProcessor(parseProcessor(entity.getPostProcessor()))
                        .nextIfTrue(entity.getNextIfTrue())
                        .nextIfFalse(entity.getNextIfFalse())
                        .condition(entity.getCondition())
                        .build())
                .toList();
    }

    private Map<String, Object> parseProcessor(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception _) {
            return Collections.emptyMap();
        }
    }

    private void validateStep(FlowStepCommand step, Map<String, String> stepIdMap) {
        try {
            FlowStepType type = FlowStepType.valueOf(step.getType().toUpperCase());
            switch (type) {
                case START -> {
                    if (step.getEndpointId() != null)
                        throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                                Map.of(Constants.REASON, "START node cannot have endpointId"));
                    if (step.getNextIfTrue() == null || !stepIdMap.containsKey(step.getNextIfTrue()))
                        throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                                Map.of(Constants.REASON, "START node must have a valid nextIfTrue target"));
                    if (step.getNextIfFalse() != null)
                        throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                                Map.of(Constants.REASON, "START node cannot have nextIfFalse"));
                }
                case ENDPOINT -> {
                    if (step.getEndpointId() == null || !endpointStore.findById(step.getEndpointId()).isPresent()) {
                        throw BusinessExceptionBuilder.exception(
                                ErrorCode.ENDPOINT_NOT_FOUND,
                                Map.of("stepId", step.getId())
                        );
                    }
                }
                case BRANCH -> {
                    if (step.getCondition() == null || step.getCondition().isBlank())
                        throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                                Map.of(Constants.REASON, "Branch node must have condition"));
                    if (step.getNextIfTrue() == null || !stepIdMap.containsKey(step.getNextIfTrue()))
                        throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                                Map.of(Constants.REASON, "Branch node has invalid nextIfTrue"));
                    if (step.getNextIfFalse() == null || !stepIdMap.containsKey(step.getNextIfFalse()))
                        throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                                Map.of(Constants.REASON, "Branch node has invalid nextIfFalse"));
                }
            }
        } catch (IllegalArgumentException e) {
            throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                    Map.of(Constants.REASON, "Unknown step type: " + step.getType()));
        }
    }

    @Override
    @Async
    public void runFlow(RunFlowCommand command) {
        RunEntity savedRun = null;

        try {
            FlowEntity flowEntity = flowStore.findById(command.getFlowId())
                    .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.FLOW_NOT_FOUND));

            ProjectEntity projectEntity = flowEntity.getProject();

            RunEntity runEntity = RunEntity.builder()
                    .flow(flowEntity)
                    .status(RunStatus.RUNNING.name())
                    .threads(command.getThreads())
                    .duration(command.getTotalDuration())
                    .rampUpDuration(command.getRampUpDuration())
                    .startedAt(LocalDateTime.now())
                    .build();
            savedRun = runStore.save(runEntity);

            Map<String, Object> environment = envVarStore
                    .findAllByEnvironmentIdAndActiveTrue(projectEntity.getEnvironment().getId())
                    .stream()
                    .collect(Collectors.toMap(EnvironmentVariableEntity::getKey, EnvironmentVariableEntity::getValue, (v1, v2) -> v2));

            if (command.getVariables() != null) {
                environment.putAll(command.getVariables());
            }

            List<FlowStepEntity> steps = flowStepStore.findAllByFlowId(command.getFlowId());
            Map<String, FlowStepEntity> stepMap = steps.stream()
                    .collect(Collectors.toMap(FlowStepEntity::getId, s -> s));

            executeFlowWithThreads(savedRun.getId(), stepMap, environment, command);

            requestLogService.ensureFlushed();

            savedRun.setStatus(RunStatus.COMPLETED.name());
            savedRun.setCompletedAt(LocalDateTime.now());
            runStore.save(savedRun);

        } catch (Exception e) {
            log.error("Run failed: {}", e.getMessage(), e);
            if (savedRun != null) {
                try { requestLogService.ensureFlushed(); } catch (Exception _) {}
                savedRun.setStatus(RunStatus.FAILED.name());
                savedRun.setCompletedAt(LocalDateTime.now());
                runStore.save(savedRun);
            }
        }
    }

    private void executeFlowWithThreads(Long runId,
                                        Map<String, FlowStepEntity> stepMap,
                                        Map<String, Object> baseEnvironment,
                                        RunFlowCommand config) {

        int threads = config.getThreads();
        int totalDuration = config.getTotalDuration();
        int rampUpDuration = config.getRampUpDuration();
        long threadStartDelay = threads > 0 ? (rampUpDuration * 1000L) / threads : 0;

        AtomicBoolean stopSignal = new AtomicBoolean(false);
        long testEndTime = System.currentTimeMillis() + (totalDuration * 1000L);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            timeoutScheduler.schedule(() -> {
                stopSignal.set(true);
                executor.shutdownNow();
            }, totalDuration + 5L, TimeUnit.SECONDS);

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int threadIndex = i;
                final long startDelay = i * threadStartDelay;

                futures.add(executor.submit(() -> {
                    try {
                        if (startDelay > 0) Thread.sleep(startDelay);
                        FlowThreadContext context = createThreadContext(threadIndex, baseEnvironment, runId, config.getCredentials());

                        while (!stopSignal.get() && System.currentTimeMillis() < testEndTime) {
                            executeFlowIteration(stepMap, context, stopSignal, testEndTime);
                        }
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        log.error("Thread fatal error", e);
                    }
                }));
            }

            for (Future<?> future : futures) {
                try { future.get(); } catch (Exception _) {}
            }
        } finally {
            timeoutScheduler.shutdownNow();
            executor.shutdownNow();
        }
    }

    private FlowThreadContext createThreadContext(int threadIndex, Map<String, Object> baseEnvironment, Long runId, List<Map<String, Object>> credentials) {
        FlowThreadContext context = new FlowThreadContext();
        context.setThreadId(threadIndex);
        context.setCookieJar(new InMemoryCookieJar());
        context.setIterationCount(0);
        context.setRunId(runId);

        Map<String, Object> threadLocalEnv = new ConcurrentHashMap<>(baseEnvironment);
        threadLocalEnv.put("THREAD_ID", threadIndex);

        if (credentials != null && !credentials.isEmpty()) {
            Map<String, Object> myCredential = credentials.get(threadIndex % credentials.size());
            if (myCredential != null) threadLocalEnv.putAll(myCredential);
        }

        context.setVariables(threadLocalEnv);
        return context;
    }

    private void executeFlowIteration(Map<String, FlowStepEntity> stepMap,
                                      FlowThreadContext context,
                                      AtomicBoolean stopSignal,
                                      long testEndTime) {

        context.incrementIteration();
        FlowStepEntity currentStep = FlowUtils.findStartNode(stepMap);

        while (currentStep != null && !stopSignal.get() && System.currentTimeMillis() < testEndTime) {
            try {
                FlowStepType stepType = FlowStepType.valueOf(currentStep.getType().toUpperCase());
                String nextStepId;

                switch (stepType) {
                    case START -> nextStepId = currentStep.getNextIfTrue();
                    case ENDPOINT -> {
                        EndpointResponse result = executeEndpointStep(currentStep, context);
                        nextStepId = result.isSuccess() && currentStep.getNextIfTrue() != null
                                ? currentStep.getNextIfTrue()
                                : currentStep.getNextIfFalse();
                    }
                    case BRANCH -> {
                        boolean conditionResult = FlowUtils.evaluateCondition(currentStep.getCondition(), context.getVariables());
                        nextStepId = conditionResult ? currentStep.getNextIfTrue() : currentStep.getNextIfFalse();
                    }
                    default -> {
                        return;
                    }
                }
                currentStep = nextStepId != null ? stepMap.get(nextStepId) : null;
            } catch (Exception e) {
                break;
            }
        }
    }

    private EndpointResponse executeEndpointStep(FlowStepEntity step, FlowThreadContext context) {
        if (step.getPreProcessor() != null && !step.getPreProcessor().isBlank()) {
            FlowUtils.process(step.getPreProcessor(), context.getVariables(), null, "pre-processor", context.getThreadId());
        }

        EndpointEntity endpointEntity = step.getEndpoint();
        var executorService = endpointExecutorServiceFactory.getExecutor(endpointEntity.getType());
        long startTime = System.currentTimeMillis();
        EndpointResponse result;

        try {
            result = executorService.execute(endpointEntity, context.getVariables(), context.getCookieJar());
            EndpointUtils.evaluateSuccessCondition(endpointEntity, result);
        } catch (Exception e) {
            result = EndpointResponse.builder()
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .success(false)
                    .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .data(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown Error"))
                    .build();
        }

        RequestLogEntity logEntity = RequestLogEntity.builder()
                .run(RunEntity.builder().id(context.getRunId()).build())
                .endpoint(endpointEntity)
                .statusCode(result.getStatusCode())
                .success(result.isSuccess())
                .responseTime(result.getResponseTimeMs())
                .request(endpointEntity.toString())
                .response(result.getRawResponse())
                .createdAt(LocalDateTime.now())
                .build();

        requestLogService.queueLog(logEntity);

        if (step.getPostProcessor() != null && !step.getPostProcessor().isBlank()) {
            FlowUtils.process(step.getPostProcessor(), context.getVariables(), result.getData(), "post-processor", context.getThreadId());
        }

        return result;
    }
}
