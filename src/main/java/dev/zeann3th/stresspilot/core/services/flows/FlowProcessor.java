package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.utils.DataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.MapAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Component
@RequiredArgsConstructor
public class FlowProcessor {

    private static final Random RANDOM = new Random();
    private static final SpelExpressionParser SPEL = new SpelExpressionParser();
    public static final String RUN_IF = "run_if";
    public static final String SKIP_IF = "skip_if";
    private final JsonMapper jsonMapper;

    public boolean shouldRun(String processorJson, Map<String, Object> variables, int threadId) {
        if (processorJson == null || processorJson.isBlank()) {
            return true;
        }
        try {
            Map<String, Object> proc = jsonMapper.readValue(processorJson, new TypeReference<>() {
            });
            if (CollectionUtils.isEmpty(proc)) {
                return true;
            }
            Object runIf = proc.get(RUN_IF);
            if (runIf != null && !evaluateCondition(String.valueOf(runIf), variables)) {
                return false;
            }
            Object skipIf = proc.get(SKIP_IF);
            return skipIf == null || !evaluateCondition(String.valueOf(skipIf), variables);
        } catch (Exception e) {
            log.error("Error evaluating step guard for thread {}: {}", threadId, e.getMessage());
            return false;
        }
    }

    public void process(String processorJson,
            Map<String, Object> variables,
            Object prevResponse,
            String processorType,
            int threadId) {
        if (processorJson == null || processorJson.isBlank())
            return;
        try {
            Map<String, Object> proc = jsonMapper.readValue(processorJson, new TypeReference<>() {
            });
            if (CollectionUtils.isEmpty(proc))
                return;

            applyDelay(proc, variables);
            applyClear(proc, variables);
            applySet(proc, variables);
            applyIncrement(proc, variables);
            applyAppend(proc, variables);
            applySerializeJson(proc, variables);
            applyInject(proc, variables);
            applyExtract(proc, variables, prevResponse);

        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error processing {} for thread {}: {}", processorType, threadId, e.getMessage());
        }
    }

    private void applyDelay(Map<String, Object> proc, Map<String, Object> variables) throws InterruptedException {
        Object delayObj = proc.containsKey("sleep") ? proc.get("sleep") : proc.get("delay");
        if (delayObj == null) {
            return;
        }

        String delay = DataUtils.replaceVariables(String.valueOf(delayObj), variables);
        long base = Long.parseLong(delay);
        long jitter = 500L + RANDOM.nextInt(501);
        Thread.sleep(base + jitter);
    }

    private void applyClear(Map<String, Object> proc, Map<String, Object> variables) {
        if (proc.get("clear") instanceof List<?> keysToClear) {
            keysToClear.forEach(key -> variables.remove(String.valueOf(key)));
        }
    }

    private void applySet(Map<String, Object> proc, Map<String, Object> variables) {
        variables.putAll(readMap(proc.get("set")));
    }

    private void applyInject(Map<String, Object> proc, Map<String, Object> variables) {
        variables.putAll(readMap(proc.get("inject")));
    }

    private void applyIncrement(Map<String, Object> proc, Map<String, Object> variables) {
        readMap(proc.get("increment")).forEach((key, value) -> {
            long current = toLong(variables.get(key));
            long delta = Long.parseLong(String.valueOf(value));
            variables.put(key, current + delta);
        });
    }

    private void applyAppend(Map<String, Object> proc, Map<String, Object> variables) {
        readMap(proc.get("append")).forEach((key, rawValue) -> {
            Object value = interpolateProcessorValue(rawValue, variables);
            List<Object> values = new ArrayList<>();
            if (variables.get(key) instanceof List<?> existingList) {
                values.addAll(existingList);
            }
            values.add(value);
            variables.put(key, values);
        });
    }

    private void applySerializeJson(Map<String, Object> proc, Map<String, Object> variables) {
        for (Map.Entry<String, Object> entry : readMap(proc.get("serialize_json")).entrySet()) {
            Object value = variables.get(String.valueOf(entry.getValue()));
            variables.put(entry.getKey(), jsonMapper.writeValueAsString(value));
        }
    }

    private void applyExtract(Map<String, Object> proc, Map<String, Object> variables, Object prevResponse) {
        if (prevResponse == null) {
            return;
        }
        readMap(proc.get("extract")).forEach((key, rawPath) -> {
            Object value = resolveExtractionPath(prevResponse, String.valueOf(rawPath));
            if (value != null) {
                variables.put(key, value);
            }
        });
    }

    private Map<String, Object> readMap(Object value) {
        if (value == null) {
            return Map.of();
        }
        return jsonMapper.convertValue(value, new TypeReference<>() {
        });
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Object resolveExtractionPath(Object prevResponse, String path) {
        if (".".equals(path) || "@".equals(path)) {
            return prevResponse;
        }
        if (path.startsWith("$")) {
            return resolvePathDollar(prevResponse, path.substring(1));
        }
        return resolvePath(prevResponse, path);
    }

    private Object interpolateProcessorValue(Object value, Map<String, Object> variables) {
        return switch (value) {
            case String str -> coerceScalar(DataUtils.replaceVariables(str, variables));
            case Map<?, ?> map -> {
                Map<String, Object> typed = jsonMapper.convertValue(map, new TypeReference<>() {
                });
                yield normalizeValue(DataUtils.replaceVariablesInMap(typed, variables));
            }
            case List<?> list -> normalizeValue(DataUtils.replaceVariablesInList(list, variables));
            case null, default -> value;
        };
    }

    private Object normalizeValue(Object value) {
        return switch (value) {
            case String str -> coerceScalar(str);
            case Map<?, ?> map -> {
                Map<String, Object> normalized = new java.util.LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    normalized.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
                }
                yield normalized;
            }
            case List<?> list -> list.stream().map(this::normalizeValue).toList();
            case null, default -> value;
        };
    }

    private Object coerceScalar(String value) {
        if (value == null) {
            return null;
        }
        if (value.matches("-?\\d+")) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException _) {
                return value;
            }
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }

    public static boolean evaluateCondition(String condition, Map<String, Object> variables) {
        if (condition == null || condition.isBlank()) {
            return false;
        }
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext(variables);
            ctx.addPropertyAccessor(new MapAccessor());
            return Boolean.TRUE.equals(SPEL.parseExpression(condition).getValue(ctx, Boolean.class));
        } catch (Exception e) {
            log.error("Error evaluating condition '{}': {}", condition, e.getMessage());
            return false;
        }
    }

    private static Object resolvePath(Object obj, String path) {
        if (obj == null || path == null || path.isBlank())
            return null;
        String[] parts = path.replaceAll("\\[(\\w+)]", ".$1").split("\\.");
        Object current = obj;
        for (String part : parts) {
            if (current == null)
                return null;
            switch (current) {
                case Map<?, ?> map -> current = map.get(part);
                case List<?> list -> {
                    try {
                        int idx = Integer.parseInt(part);
                        current = (idx >= 0 && idx < list.size()) ? list.get(idx) : null;
                    } catch (NumberFormatException _) {
                        return null;
                    }
                }
                default -> {
                    return null;
                }
            }
        }
        return current;
    }

    private Object resolvePathDollar(Object obj, String spec) {
        if (obj == null || spec == null || spec.isBlank()) {
            return null;
        }
        List<Map.Entry<String, Object>> flat = new ArrayList<>();
        DataUtils.flattenObject(obj, "", flat);

        return spec.contains(".")
                ? firstExactOrSuffix(flat, spec)
                : firstLastSegment(flat, spec);
    }

    private static Object firstExactOrSuffix(List<Map.Entry<String, Object>> flat, String spec) {
        Object exact = firstValueMatching(flat, spec::equals);
        return exact != null ? exact : firstValueMatching(flat, path -> path.endsWith("." + spec));
    }

    private static Object firstLastSegment(List<Map.Entry<String, Object>> flat, String spec) {
        return firstValueMatching(flat, path -> lastSegment(path).equals(spec));
    }

    private static Object firstValueMatching(List<Map.Entry<String, Object>> flat,
            java.util.function.Predicate<String> predicate) {
        for (Map.Entry<String, Object> entry : flat) {
            if (predicate.test(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String lastSegment(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }
}
