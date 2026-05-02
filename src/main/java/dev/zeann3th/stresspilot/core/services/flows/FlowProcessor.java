package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.utils.DataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final JsonMapper jsonMapper;

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

            if (proc.containsKey("sleep")) {
                long base = Long.parseLong(proc.get("sleep").toString());
                long jitter = 500L + RANDOM.nextInt(501);
                Thread.sleep(base + jitter);
            }

            if (proc.get("clear") instanceof List<?> keysToClear) {
                keysToClear.forEach(key -> variables.remove(String.valueOf(key)));
            }

            if (proc.containsKey("inject")) {
                Object injectObj = proc.get("inject");
                if (injectObj != null) {
                    Map<String, Object> inject = jsonMapper.convertValue(injectObj, new TypeReference<>() {
                    });
                    variables.putAll(inject);
                }
            }

            if (proc.containsKey("extract") && prevResponse != null) {
                Object extractObj = proc.get("extract");
                if (extractObj != null) {
                    Map<String, Object> extract = jsonMapper.convertValue(extractObj, new TypeReference<>() {
                    });
                    for (Map.Entry<String, Object> entry : extract.entrySet()) {
                        String key = entry.getKey();
                        String path = String.valueOf(entry.getValue());
                        Object value;
                        if (".".equals(path) || "@".equals(path)) {
                            value = prevResponse;
                        } else if (path.startsWith("$")) {
                            value = resolvePathDollar(prevResponse, path.substring(1));
                        } else {
                            value = resolvePath(prevResponse, path);
                        }
                        if (value != null)
                            variables.put(key, value);
                    }
                }
            }

        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error processing {} for thread {}: {}", processorType, threadId, e.getMessage());
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
        if (obj == null || spec == null || spec.isBlank())
            return null;
        List<Map.Entry<String, Object>> flat = new ArrayList<>();
        DataUtils.flattenObject(obj, "", flat);

        if (spec.contains(".")) {
            for (Map.Entry<String, Object> e : flat) {
                if (e.getKey().equals(spec))
                    return e.getValue();
            }
            String suffix = "." + spec;
            for (Map.Entry<String, Object> e : flat) {
                if (e.getKey().endsWith(suffix))
                    return e.getValue();
            }
        } else {
            for (Map.Entry<String, Object> e : flat) {
                String p = e.getKey();
                String last = p.contains(".") ? p.substring(p.lastIndexOf('.') + 1) : p;
                if (last.equals(spec))
                    return e.getValue();
            }
        }
        return null;
    }
}
