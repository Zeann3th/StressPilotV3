package dev.zeann3th.stresspilot.core.services.flows.impl;

import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.commands.flow.CreateFlowCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.FlowStepCommand;
import dev.zeann3th.stresspilot.core.domain.commands.flow.RunFlowCommand;

import dev.zeann3th.stresspilot.core.domain.entities.*;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.*;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import dev.zeann3th.stresspilot.core.services.executors.context.HttpExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.FlowService;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandlerFactory;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j(topic = "[FlowService]")
@Service
@RequiredArgsConstructor
public class FlowServiceImpl implements FlowService {

    private final FlowStore flowStore;
    private final FlowStepStore flowStepStore;
    private final ProjectStore projectStore;
    private final RunStore runStore;
    private final EndpointStore endpointStore;
    private final EnvironmentVariableStore envVarStore;
    private final RequestLogService requestLogService;
    private final FlowNodeHandlerFactory nodeHandlerFactory;
    private final JsonMapper jsonMapper;

    @Override
    public Page<FlowEntity> getListFlow(Long projectId, String name, Pageable pageable) {
        return flowStore.findAllByCondition(projectId, name, pageable);
    }

    @Override
    public FlowEntity getFlowDetail(Long flowId) {
        return flowStore.findById(flowId)
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
                .build();

        return flowStore.save(entity);
    }

    @Override
    @Transactional
    public FlowEntity updateFlow(Long flowId, Map<String, Object> patch) {
        FlowEntity entity = flowStore.findById(flowId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0003));
        Map<String, Object> safe = patch.entrySet().stream()
                .filter(e -> !Set.of("id", "projectId", "project", "steps").contains(e.getKey()))
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

        return flowStore.findById(flowId).orElse(flow);
    }

    @Override
    @Async
    @Transactional
    @SuppressWarnings("java:S3776")
    public void runFlow(Long flowId, RunFlowCommand runFlowCommand) {
        FlowEntity flow = flowStore.findById(flowId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0003));

        List<FlowStepEntity> steps = flowStepStore.findAllByFlowId(flowId);
        if (steps.isEmpty())
            throw CommandExceptionBuilder.exception(ErrorCode.ER0004,
                    Map.of(Constants.REASON, "Flow has no configured steps"));

        sortSteps(steps);
        validateStartStep(steps);

        Map<String, FlowStepEntity> stepMap = steps.stream()
                .collect(Collectors.toMap(FlowStepEntity::getId, s -> s));

        RunEntity run = runStore.save(RunEntity.builder()
                .flow(flow)
                .status(RunStatus.RUNNING.name())
                .threads(runFlowCommand.getThreads())
                .duration(runFlowCommand.getTotalDuration())
                .rampUpDuration(runFlowCommand.getRampUpDuration())
                .startedAt(LocalDateTime.now())
                .build());

        ProjectEntity project = flow.getProject();
        Map<String, Object> baseEnv = envVarStore
                .findAllByEnvironmentIdAndActiveTrue(project.getEnvironment().getId())
                .stream()
                .collect(Collectors.toMap(
                        EnvironmentVariableEntity::getKey,
                        EnvironmentVariableEntity::getValue,
                        (_, v2) -> v2, HashMap::new));
        if (runFlowCommand.getVariables() != null)
            baseEnv.putAll(runFlowCommand.getVariables());

        AtomicBoolean stopSignal = new AtomicBoolean(false);

        int threads = Math.max(1, runFlowCommand.getThreads());
        ExecutorService pool = Executors.newFixedThreadPool(threads,
                r -> new Thread(r, "sp-worker-" + run.getId()));

        try {
            long totalMs = (long) runFlowCommand.getTotalDuration() * 1000;
            long rampUpMs = (long) runFlowCommand.getRampUpDuration() * 1000;
            long rampDelay = threads > 1 ? rampUpMs / (threads - 1) : 0;

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int threadId = i;

                Map<String, Object> threadEnv = new HashMap<>(baseEnv);
                if (runFlowCommand.getCredentials() != null && !runFlowCommand.getCredentials().isEmpty()) {
                    threadEnv.putAll(runFlowCommand.getCredentials().get(i % runFlowCommand.getCredentials().size()));
                }

                if (i > 0 && rampDelay > 0) {
                    try {
                        Thread.sleep(rampDelay);
                    } catch (InterruptedException _) {
                        log.warn("Ramp-up sleep interrupted at thread {}; submitting remaining workers without delay",
                                i);
                    }
                }

                futures.add(pool.submit(() -> runWorker(threadId, run, stepMap, threadEnv, totalMs, stopSignal)));
            }

            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception e) {
                    log.warn("Worker thread encountered error: {}", e.getMessage());
                }
            }

        } finally {
            pool.shutdownNow();
        }

        requestLogService.ensureFlushed();

        run.setStatus(RunStatus.COMPLETED.name());
        run.setCompletedAt(LocalDateTime.now());
        runStore.save(run);
    }

    private void runWorker(int threadId, RunEntity run,
            Map<String, FlowStepEntity> stepMap,
            Map<String, Object> environment,
            long totalMs, AtomicBoolean stop) {
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setThreadId(threadId);
        ctx.setRunId(run.getId());
        ctx.setRun(run);
        ctx.setVariables(new ConcurrentHashMap<>(environment));
        ctx.setExecutionContext(new HttpExecutionContext());

        FlowStepEntity startStep = findStartNode(stepMap);
        if (startStep == null)
            return;

        long deadline = System.currentTimeMillis() + totalMs;

        while (!stop.get() && System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
            try {
                executeIteration(startStep, stepMap, ctx);
                ctx.incrementIteration();
            } catch (Exception e) {
                log.error("Thread {} iteration error: {}", threadId, e.getMessage(), e);
            }
        }

        ctx.getExecutionContext().clear();
    }

    private void executeIteration(FlowStepEntity startStep,
            Map<String, FlowStepEntity> stepMap,
            FlowExecutionContext ctx) {
        FlowStepEntity current = startStep;
        Set<String> visited = new LinkedHashSet<>();

        while (current != null) {
            if (visited.contains(current.getId())) {
                log.warn("Cycle detected at step {} — breaking iteration", current.getId());
                break;
            }
            visited.add(current.getId());

            FlowStepType type;
            try {
                type = FlowStepType.valueOf(current.getType().toUpperCase());
            } catch (IllegalArgumentException _) {
                log.error("Unknown step type: {}", current.getType());
                break;
            }

            String nextId = nodeHandlerFactory.getHandler(type)
                    .handle(current, stepMap, ctx);

            current = nextId != null ? stepMap.get(nextId) : null;
        }
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

    public static void validateStartStep(List<FlowStepEntity> steps) {
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

    public static void detectInfiniteLoop(List<FlowStepEntity> steps, Map<String, String> stepIdMap) {
        Map<String, List<String>> graph = new HashMap<>();
        Set<String> terminals = new HashSet<>();

        for (FlowStepEntity step : steps) {
            String id = stepIdMap.get(step.getId());
            List<String> nexts = new ArrayList<>();
            if (step.getNextIfTrue() != null)
                nexts.add(stepIdMap.get(step.getNextIfTrue()));
            if (step.getNextIfFalse() != null)
                nexts.add(stepIdMap.get(step.getNextIfFalse()));
            graph.put(id, nexts);
            if (FlowStepType.ENDPOINT.name().equalsIgnoreCase(step.getType()) && nexts.isEmpty())
                terminals.add(id);
        }

        if (terminals.isEmpty())
            throw CommandExceptionBuilder.exception(ErrorCode.ER0004,
                    Map.of(Constants.REASON, "No terminal ENDPOINT found — flow would be infinite"));

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

    public static void sortSteps(List<FlowStepEntity> steps) {
        steps.sort((a, b) -> {
            if (FlowStepType.START.name().equalsIgnoreCase(a.getType()))
                return -1;
            if (FlowStepType.START.name().equalsIgnoreCase(b.getType()))
                return 1;
            return a.getId().compareTo(b.getId());
        });
    }

    public static FlowStepEntity findStartNode(Map<String, FlowStepEntity> stepMap) {
        return stepMap.values().stream()
                .filter(s -> FlowStepType.START.name().equalsIgnoreCase(s.getType()))
                .findFirst()
                .orElse(null);
    }
}
