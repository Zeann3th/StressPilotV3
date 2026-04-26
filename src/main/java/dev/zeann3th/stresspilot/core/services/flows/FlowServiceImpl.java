package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.commands.flow.CreateFlowCommand;
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
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import dev.zeann3th.stresspilot.core.utils.SnowflakeId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.*;
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
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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
        Map<String, String> idMap = preview.stream()
                .collect(Collectors.toMap(FlowStepEntity::getId, FlowStepEntity::getId));
        detectInfiniteLoop(preview, idMap);

        flowStepStore.deleteAllByFlowId(flowId);
        flowStepStore.saveAll(preview);

        return flowStore.findByIdWithSteps(flowId).orElse(flow);
    }

    @EventListener
    public void handleInterruptRunEvent(InterruptRunEvent event) {
        boolean stopped = activeRunRegistry.interruptRun(event.runId());

        if (stopped) {
            log.info("received abort signal. Killing threads for run {}", event.runId());
        } else {
            log.warn("received stop event for run {}, but it is not active in memory.", event.runId());
        }
    }

    @Override
    @Transactional
    public String runFlow(Long flowId, RunFlowCommand runFlowCommand) {
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

        Map<String, Object> baseEnv = envVarStore
                .findAllByEnvironmentIdAndActiveTrue(project.getEnvironmentId())
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
                .rampUpDuration(runFlowCommand.getRampUpDuration())
                .startedAt(LocalDateTime.now())
                .build());

        FlowExecutionData executionData = FlowExecutionData.builder()
                .runId(runId)
                .run(run)
                .flowType(flow.getType())
                .steps(steps)
                .baseEnvironment(baseEnv)
                .command(runFlowCommand)
                .build();

        flowAsyncRunner.run(executionData);
        return runId;
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

    private static void detectInfiniteLoop(List<FlowStepEntity> steps, Map<String, String> stepIdMap) {
        Map<String, List<String>> graph = new HashMap<>();
        Set<String> terminals = new HashSet<>();

        Set<String> allowedTerminalTypes = Set.of(
                FlowStepType.ENDPOINT.name(),
                FlowStepType.SUBFLOW.name()
        );

        for (FlowStepEntity step : steps) {
            String id = stepIdMap.get(step.getId());
            List<String> nexts = new ArrayList<>();
            if (step.getNextIfTrue() != null)
                nexts.add(stepIdMap.get(step.getNextIfTrue()));
            if (step.getNextIfFalse() != null)
                nexts.add(stepIdMap.get(step.getNextIfFalse()));
            graph.put(id, nexts);

            if (nexts.isEmpty()) {
                if (!allowedTerminalTypes.contains(step.getType().toUpperCase())) {
                    throw CommandExceptionBuilder.exception(ErrorCode.ER0004,
                            Map.of(Constants.REASON, "Step " + id + " of type " + step.getType() + " cannot be a terminal node"));
                }
                terminals.add(id);
            }
        }

        if (terminals.isEmpty())
            throw CommandExceptionBuilder.exception(ErrorCode.ER0004,
                    Map.of(Constants.REASON, "No terminal node found — flow would be infinite"));

        Map<String, Boolean> memo = new HashMap<>();
        for (String node : graph.keySet()) {
            if (!canReachEndpoint(node, graph, terminals, new HashSet<>(), memo))
                throw CommandExceptionBuilder.exception(ErrorCode.ER0004,
                        Map.of(Constants.REASON, "Infinite cycle — node " + node + " cannot reach a terminal"));
        }
    }

    private static boolean canReachEndpoint(String node, Map<String, List<String>> graph,
            Set<String> terminals, Set<String> visiting,
            Map<String, Boolean> memo) {
        if (memo.containsKey(node))
            return memo.get(node);
        if (terminals.contains(node)) {
            memo.put(node, true);
            return true;
        }
        if (visiting.contains(node))
            return false;
        visiting.add(node);
        for (String next : graph.getOrDefault(node, List.of())) {
            if (next != null && canReachEndpoint(next, graph, terminals, visiting, memo)) {
                memo.put(node, true);
                visiting.remove(node);
                return true;
            }
        }
        visiting.remove(node);
        memo.put(node, false);
        return false;
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
