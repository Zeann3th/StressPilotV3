package dev.zeann3th.stresspilot.core.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.exception.BusinessExceptionBuilder;
import dev.zeann3th.stresspilot.core.domain.commands.flow.FlowStepCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Slf4j(topic = "[Flow Utils]")
@SuppressWarnings("java:S3776")
public class FlowUtils {
    private static final Random RANDOM = new Random();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private FlowUtils() {}

    public static void process(String processorJson, Map<String, Object> variables, Object prevResp, String type, int threadId) {
        if (processorJson == null || processorJson.isBlank()) return;

        try {
            Map<String, Object> processorMap = objectMapper.readValue(processorJson, new TypeReference<>() {});
            if (CollectionUtils.isEmpty(processorMap)) return;

            // --- 1. SLEEP ---
            if (processorMap.containsKey("sleep")) {
                long baseDelayMs = Long.parseLong(processorMap.get("sleep").toString());
                long randomExtra = 500 + (long) RANDOM.nextInt(501);
                Thread.sleep(baseDelayMs + randomExtra);
            }

            // --- 2. INJECT ---
            if (processorMap.containsKey("inject")) {
                Object injectObj = processorMap.get("inject");
                if (injectObj != null) {
                    Map<String, Object> injectMap = objectMapper.convertValue(injectObj, new TypeReference<>() {});
                    variables.putAll(injectMap);
                }
            }

            // --- 3. EXTRACT ---
            if (processorMap.containsKey("extract") && prevResp != null) {
                Object extractObj = processorMap.get("extract");
                if (extractObj != null) {
                    Map<String, Object> extractMap = objectMapper.convertValue(extractObj, new TypeReference<>() {});
                    for (Map.Entry<String, Object> entry : extractMap.entrySet()) {
                        String targetKey = entry.getKey();
                        String path = String.valueOf(entry.getValue());
                        Object value;
                        if (path.equals(".") || path.equals("@")) {
                            value = prevResp;
                        } else if (path.startsWith("$")) {
                            String spec = path.substring(1);
                            value = resolvePathDollar(prevResp, spec);
                        } else {
                            value = resolvePath(prevResp, path);
                        }
                        if (value != null) {
                            variables.put(targetKey, value);
                        }
                    }
                }
            }

        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error processing {} for thread {}: {}", type, threadId, e.getMessage());
        }
    }

    public static boolean evaluateCondition(String condition, Map<String, Object> variables) {
        if (condition == null || condition.isBlank()) return false;
        try {
            StandardEvaluationContext evalContext = new StandardEvaluationContext(variables);
            evalContext.addPropertyAccessor(new MapAccessor());
            return Boolean.TRUE.equals(PARSER.parseExpression(condition).getValue(evalContext, Boolean.class));
        } catch (Exception e) {
            log.error("Error evaluating condition '{}': {}", condition, e.getMessage());
            return false;
        }
    }

    private static Object resolvePath(Object obj, String path) {
        if (obj == null || path == null || path.isBlank()) return null;

        String[] parts = path.replaceAll("\\[(\\w+)\\]", ".$1").split("\\.");
        Object current = obj;

        for (String part : parts) {
            if (current == null) return null;

            switch (current) {
                case Map<?, ?> map -> current = map.get(part);
                case List<?> list -> {
                    try {
                        int index = Integer.parseInt(part);
                        if (index >= 0 && index < list.size()) {
                            current = list.get(index);
                        } else {
                            return null;
                        }
                    } catch (NumberFormatException _) {
                        return null;
                    }
                }
                default -> { return null; }
            }
        }

        return current;
    }

    private static Object resolvePathDollar(Object obj, String spec) {
        if (obj == null || spec == null || spec.isBlank()) return null;

        List<Map.Entry<String, Object>> flat = new ArrayList<>();
        flattenResponse(obj, "", flat);

        if (spec.contains(".")) {
            for (Map.Entry<String, Object> e : flat) {
                if (e.getKey().equals(spec)) return e.getValue();
            }
            String suffix = "." + spec;
            for (Map.Entry<String, Object> e : flat) {
                if (e.getKey().endsWith(suffix)) return e.getValue();
            }
            for (Map.Entry<String, Object> e : flat) {
                if (e.getKey().contains(spec)) return e.getValue();
            }
            return null;
        }

        for (Map.Entry<String, Object> e : flat) {
            String p = e.getKey();
            String last = p.contains(".") ? p.substring(p.lastIndexOf('.') + 1) : p;
            if (last.equals(spec)) return e.getValue();
        }

        return null;
    }

    private static void flattenResponse(Object current, String path, List<Map.Entry<String, Object>> out) {
        switch (current) {
            case Map<?, ?> map -> {
                for (Map.Entry<?, ?> en : map.entrySet()) {
                    String key = String.valueOf(en.getKey());
                    String newPath = path.isEmpty() ? key : path + "." + key;
                    flattenResponse(en.getValue(), newPath, out);
                }
            }
            case List<?> list -> {
                for (int i = 0; i < list.size(); i++) {
                    String newPath = path.isEmpty() ? String.valueOf(i) : path + "." + i;
                    flattenResponse(list.get(i), newPath, out);
                }
            }
            default -> out.add(new AbstractMap.SimpleEntry<>(path, current));
        }
    }

    public static void validateStartStep(List<FlowStepCommand> steps) {
        long startCount = steps.stream().filter(s -> FlowStepType.START.name().equalsIgnoreCase(s.getType())).count();
        if (startCount == 0)
            throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST, Map.of(Constants.REASON, "Flow must contain one START node (none found)"));
        if (startCount > 1)
            throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST, Map.of(Constants.REASON, "Flow must contain one START node (found " + startCount + ")"));
    }

    public static void detectInfiniteLoop(List<FlowStepCommand> steps, Map<String, String> stepIdMap) {
        Map<String, List<String>> graph = new HashMap<>();
        Set<String> terminalNodes = new HashSet<>();
        for (FlowStepCommand dto : steps) {
            String id = stepIdMap.get(dto.getId());
            List<String> nexts = new ArrayList<>();
            if (dto.getNextIfTrue() != null) nexts.add(stepIdMap.get(dto.getNextIfTrue()));
            if (dto.getNextIfFalse() != null) nexts.add(stepIdMap.get(dto.getNextIfFalse()));
            graph.put(id, nexts);
            if (FlowStepType.ENDPOINT.name().equalsIgnoreCase(dto.getType()) && nexts.isEmpty())
                terminalNodes.add(id);
        }
        if (terminalNodes.isEmpty())
            throw BusinessExceptionBuilder.exception(ErrorCode.FLOW_CONFIGURATION_ERROR, Map.of("reason", "No terminal endpoint found — flow would be infinite"));
        Map<String, Boolean> memo = new HashMap<>();
        for (String node : graph.keySet()) {
            if (!canReachEndpoint(node, graph, terminalNodes, new HashSet<>(), memo))
                throw BusinessExceptionBuilder.exception(ErrorCode.FLOW_CONFIGURATION_ERROR, Map.of("reason", "Infinite cycle detected — node " + node + " cannot reach terminal endpoint"));
        }
    }

    private static boolean canReachEndpoint(String node, Map<String, List<String>> graph, Set<String> endpoints,
                                            Set<String> visiting, Map<String, Boolean> memo) {
        if (memo.containsKey(node)) return memo.get(node);
        if (endpoints.contains(node)) { memo.put(node, true); return true; }
        if (visiting.contains(node)) return false;
        visiting.add(node);
        for (String next : graph.getOrDefault(node, List.of())) {
            if (next != null && canReachEndpoint(next, graph, endpoints, visiting, memo)) {
                memo.put(node, true); visiting.remove(node); return true;
            }
        }
        visiting.remove(node); memo.put(node, false); return false;
    }

    public static void sortSteps(List<FlowStepEntity> steps) {
        steps.sort((a, b) -> {
            if (FlowStepType.START.name().equalsIgnoreCase(a.getType())) return -1;
            if (FlowStepType.START.name().equalsIgnoreCase(b.getType())) return 1;
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
