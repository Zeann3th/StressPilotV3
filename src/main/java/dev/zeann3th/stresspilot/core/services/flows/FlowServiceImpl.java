package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.CreateFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.DryRunStepCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.DryRunStepResult;
import dev.zeann3th.stresspilot.core.domain.commands.flow.FlowStepCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.*;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.enums.FlowType;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.domain.events.InterruptRunEvent;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.*;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.executors.context.BaseExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandlerFactory;
import dev.zeann3th.stresspilot.core.services.flows.nodes.NodeHandlerResult;
import dev.zeann3th.stresspilot.core.services.flows.strategies.distributed.DistributedEventPublisher;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.SnowflakeId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j(topic = "[FlowService]")
@Service
@RequiredArgsConstructor
public class FlowServiceImpl implements FlowService {

    private final FlowStore flowStore;
    private final FlowStepStore flowStepStore;
    private final ProjectStore projectStore;
    private final EndpointStore endpointStore;
    private final EnvironmentVariableStore envVarStore;
    private final RunStore runStore;
    private final JsonMapper jsonMapper;
    private final ActiveRunRegistry activeRunRegistry;
    private final FlowAsyncRunner flowAsyncRunner;
    private final SnowflakeId snowflakeId;
    private final ObjectProvider<DistributedEventPublisher> distributedEventPublisherProvider;
    private final FlowProcessor flowProcessor;
    private final FlowNodeHandlerFactory nodeHandlerFactory;

    @Override
    @Transactional(readOnly = true)
    public Page<FlowEntity> getListFlow(Long projectId, String name, Pageable pageable) {
        return flowStore.findAllByCondition(projectId, name, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public FlowEntity getFlowDetail(Long flowId) {
        return flowStore.findByIdWithSteps(flowId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0003));
    }

    @Override
    @Transactional
    public FlowEntity createFlow(CreateFlowCommand createFlowCommand) {
        ProjectEntity project = projectStore.findById(createFlowCommand.getProjectId())
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));

        FlowEntity entity = FlowEntity.builder()
                .project(project)
                .name(createFlowCommand.getName())
                .description(createFlowCommand.getDescription())
                .type(createFlowCommand.getType() != null ? createFlowCommand.getType() : FlowType.DEFAULT.name())
                .build();

        return flowStore.save(entity);
    }

    @Override
    @Transactional
    public FlowEntity updateFlow(Long flowId, Map<String, Object> patch) {
        FlowEntity entity = flowStore.findByIdWithSteps(flowId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0003));
        Map<String, Object> safe = patch.entrySet().stream()
                .filter(e -> !Set.of("id", "projectId", "project", "steps", "type").contains(e.getKey()))
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
        try {
            jsonMapper.updateValue(entity, safe);
            return flowStore.save(entity);
        } catch (Exception ex) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0019,
                    Map.of(Constants.REASON, ex.getMessage()));
        }
    }

    @Override
    @Transactional
    public void deleteFlow(Long flowId) {
        if (flowStore.findById(flowId).isEmpty())
            throw CommandExceptionBuilder.exception(ErrorCode.ER0003);
        flowStepStore.deleteAllByFlowId(flowId);
        flowStore.deleteById(flowId);
    }

    @Override
    @Transactional
    public FlowEntity configureFlow(Long flowId, List<FlowStepCommand> stepCmds) {
        FlowEntity flow = flowStore.findById(flowId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0003));

        List<FlowStepEntity> preview = stepsFromCommands(flow, stepCmds);
        validateStartStep(preview);

        flowStepStore.deleteAllByFlowId(flowId);
        flowStepStore.saveAll(preview);

        return flowStore.findByIdWithSteps(flowId).orElse(flow);
    }

    @EventListener
    public void handleInterruptRunEvent(InterruptRunEvent event) {
        boolean stopped = activeRunRegistry.interruptRun(event.runId());
        DistributedEventPublisher eventPublisher = distributedEventPublisherProvider.getIfAvailable();
        if (eventPublisher != null && eventPublisher.isAvailable()) {
            try {
                eventPublisher.publishStop(event.runId());
            } catch (RuntimeException ex) {
                log.warn("failed to publish distributed stop for run {}: {}", event.runId(), ex.getMessage());
            }
        }

        if (stopped) {
            log.info("received abort signal. Killing threads for run {}", event.runId());
        } else {
            log.warn("received stop event for run {}, but it is not active in memory.", event.runId());
        }
    }

    @Override
    @Transactional
    public String runFlow(Long flowId, RunFlowCommand runFlowCommand) {
        if (runFlowCommand.getTotalDuration() == null && runFlowCommand.getLoopCount() == null) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0001,
                    Map.of(Constants.REASON, "totalDuration or loopCount must be set"));
        }
        if (runFlowCommand.getTotalDuration() != null && runFlowCommand.getTotalDuration() < 1) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0001,
                    Map.of(Constants.REASON, "totalDuration must be at least 1 when set"));
        }
        if (runFlowCommand.getLoopCount() != null && runFlowCommand.getLoopCount() < 1) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0001,
                    Map.of(Constants.REASON, "loopCount must be at least 1 when set"));
        }

        FlowEntity flow = flowStore.findById(flowId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0003));

        List<FlowStepEntity> steps = flowStepStore.findAllByFlowIdWithEndpoint(flowId);
        if (steps.isEmpty())
            throw CommandExceptionBuilder.exception(ErrorCode.ER0004,
                    Map.of(Constants.REASON, "Flow has no configured steps"));

        sortSteps(steps);
        validateStartStep(steps);

        ProjectEntity project = projectStore.findById(flow.getProjectId())
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));

        Long resolvedEnvironmentId = runFlowCommand.getEnvironmentId() != null
                ? runFlowCommand.getEnvironmentId()
                : project.getActiveEnvironmentId();
        if (resolvedEnvironmentId == null) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0016,
                    Map.of(Constants.ID, "active"));
        }

        Map<String, Object> baseEnv = envVarStore
                .findAllByEnvironmentIdAndActiveTrue(resolvedEnvironmentId)
                .stream()
                .collect(Collectors.toMap(
                        EnvironmentVariableEntity::getKey,
                        EnvironmentVariableEntity::getValue,
                        (_, v2) -> v2, HashMap::new));
        if (runFlowCommand.getVariables() != null) {
            baseEnv.putAll(runFlowCommand.getVariables());
        }

        String runId = String.valueOf(snowflakeId.nextId());
        RunEntity run = runStore.save(RunEntity.builder()
                .id(runId)
                .flow(flow)
                .status(RunStatus.RUNNING.name())
                .threads(runFlowCommand.getThreads())
                .duration(runFlowCommand.getTotalDuration())
                .loopCount(runFlowCommand.getLoopCount())
                .rampUpDuration(runFlowCommand.getRampUpDuration())
                .startedAt(LocalDateTime.now())
                .build());

        FlowExecutionContext executionContext = FlowExecutionContext.builder()
                .runId(runId)
                .run(run)
                .flowType(flow.getType())
                .steps(steps)
                .baseEnvironment(baseEnv)
                .command(runFlowCommand)
                .build();

        flowAsyncRunner.run(executionContext);
        return runId;
    }

    @Override
    @Transactional(readOnly = true)
    public DryRunStepResult dryRunStep(Long flowId, DryRunStepCommand command) {
        FlowEntity flow = flowStore.findById(flowId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0003));

        List<FlowStepEntity> steps = flowStepStore.findAllByFlowIdWithEndpoint(flowId);
        if (steps.isEmpty()) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0004,
                    Map.of(Constants.REASON, "Flow has no configured steps"));
        }

        Map<String, FlowStepEntity> stepMap = steps.stream()
                .collect(Collectors.toMap(FlowStepEntity::getId, Function.identity()));
        FlowStepEntity selected = stepMap.get(command.getStepId());
        if (selected == null) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0004,
                    Map.of(Constants.REASON, "Step " + command.getStepId() + " was not found in flow"));
        }

        Map<String, Object> variables = resolveDryRunVariables(flow, command);
        String correlationId = String.valueOf(snowflakeId.nextId());
        FlowExecutionContext context = FlowExecutionContext.builder()
                .runId("dry-run-" + correlationId)
                .run(null)
                .flowType(flow.getType())
                .steps(steps)
                .baseEnvironment(variables)
                .variables(new java.util.concurrent.ConcurrentHashMap<>(variables))
                .executionContext(new BaseExecutionContext())
                .persistRequestLogs(false)
                .build();
        context.setCorrelationId(correlationId);

        NodeHandlerResult result;
        if (flowProcessor.shouldRun(selected.getPreProcessor(), context.getVariables(), 0)) {
            flowProcessor.process(selected.getPreProcessor(), context.getVariables(),
                    null, "dry-run pre-processor", 0);
            result = nodeHandlerFactory.getHandler(selected.getType().toUpperCase())
                    .handle(selected, stepMap, context);
            flowProcessor.process(selected.getPostProcessor(), context.getVariables(),
                    result.outputData(), "dry-run post-processor", 0);
        } else {
            String next = selected.getNextIfTrue() != null ? selected.getNextIfTrue() : selected.getNextIfFalse();
            result = NodeHandlerResult.of(next);
        }

        return DryRunStepResult.builder()
                .stepId(selected.getId())
                .stepType(selected.getType())
                .nextStepId(result.nextId())
                .correlationId(correlationId)
                .persisted(false)
                .outputData(result.outputData())
                .variables(new LinkedHashMap<>(context.getVariables()))
                .requestLogs(List.copyOf(context.getDryRunRequestLogs()))
                .build();
    }

    private Map<String, Object> resolveDryRunVariables(FlowEntity flow, DryRunStepCommand command) {
        ProjectEntity project = projectStore.findById(flow.getProjectId())
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0002));
        Long resolvedEnvironmentId = command.getEnvironmentId() != null
                ? command.getEnvironmentId()
                : project.getActiveEnvironmentId();
        if (resolvedEnvironmentId == null) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0016,
                    Map.of(Constants.ID, "active"));
        }

        Map<String, Object> variables = envVarStore
                .findAllByEnvironmentIdAndActiveTrue(resolvedEnvironmentId)
                .stream()
                .collect(Collectors.toMap(
                        EnvironmentVariableEntity::getKey,
                        EnvironmentVariableEntity::getValue,
                        (_, v2) -> v2, LinkedHashMap::new));
        if (command.getVariables() != null) {
            variables.putAll(command.getVariables());
        }
        if (command.getTemporaryVariables() != null) {
            variables.putAll(command.getTemporaryVariables());
        }
        return variables;
    }

    private List<FlowStepEntity> stepsFromCommands(FlowEntity flow, List<FlowStepCommand> cmds) {
        List<FlowStepEntity> result = new ArrayList<>();
        for (FlowStepCommand cmd : cmds) {
            EndpointEntity endpoint = null;
            if (FlowStepType.ENDPOINT.name().equalsIgnoreCase(cmd.getType()) && cmd.getEndpointId() != null) {
                endpoint = endpointStore.findById(cmd.getEndpointId())
                        .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0005,
                                Map.of(Constants.ID, cmd.getEndpointId())));
            }
            FlowStepEntity step = FlowStepEntity.builder()
                    .id(cmd.getId() != null ? cmd.getId() : UUID.randomUUID().toString())
                    .flow(flow)
                    .type(cmd.getType().toUpperCase())
                    .endpoint(endpoint)
                    .preProcessor(
                            cmd.getPreProcessor() != null ? DataUtils.parseObjToString(cmd.getPreProcessor()) : null)
                    .postProcessor(
                            cmd.getPostProcessor() != null ? DataUtils.parseObjToString(cmd.getPostProcessor()) : null)
                    .nextIfTrue(cmd.getNextIfTrue())
                    .nextIfFalse(cmd.getNextIfFalse())
                    .condition(cmd.getCondition())
                    .build();
            result.add(step);
        }
        return result;
    }

    private static void validateStartStep(List<FlowStepEntity> steps) {
        long count = steps.stream()
                .filter(s -> FlowStepType.START.name().equalsIgnoreCase(s.getType()))
                .count();
        if (count == 0)
            throw CommandExceptionBuilder.exception(ErrorCode.ER0020,
                    Map.of(Constants.REASON, "Flow must have exactly one START node (found none)"));
        if (count > 1)
            throw CommandExceptionBuilder.exception(ErrorCode.ER0020,
                    Map.of(Constants.REASON, "Flow must have exactly one START node (found " + count + ")"));
    }

    private static void sortSteps(List<FlowStepEntity> steps) {
        steps.sort((a, b) -> {
            if (FlowStepType.START.name().equalsIgnoreCase(a.getType()))
                return -1;
            if (FlowStepType.START.name().equalsIgnoreCase(b.getType()))
                return 1;
            return a.getId().compareTo(b.getId());
        });
    }
}
