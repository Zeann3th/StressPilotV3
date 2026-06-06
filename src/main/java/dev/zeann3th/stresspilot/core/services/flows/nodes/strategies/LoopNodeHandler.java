package dev.zeann3th.stresspilot.core.services.flows.nodes.strategies;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandler;
import dev.zeann3th.stresspilot.core.services.flows.nodes.NodeHandlerResult;
import dev.zeann3th.stresspilot.core.utils.DataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "[LoopNodeHandler]")
@Component
@RequiredArgsConstructor
public class LoopNodeHandler implements FlowNodeHandler {

    private final JsonMapper jsonMapper;

    @Override
    public String getSupportedType() {
        return FlowStepType.LOOP.name();
    }

    @Override
    public NodeHandlerResult handle(FlowStepEntity step,
            Map<String, FlowStepEntity> stepMap,
            FlowExecutionContext context) {
        LoopConfig config = readConfig(step);
        if (config.body() == null || config.body().isBlank()) {
            return NodeHandlerResult.of(step.getNextIfTrue());
        }

        List<?> values = resolveValues(config, context.getVariables());
        String cursorKey = "__loop_" + step.getId() + "_cursor";
        int cursor = readCursor(context.getVariables().get(cursorKey));

        if (cursor >= values.size()) {
            context.getVariables().remove(cursorKey);
            clearLoopVariables(config, context.getVariables());
            return NodeHandlerResult.of(step.getNextIfTrue());
        }

        Object item = values.get(cursor);
        context.getVariables().put(config.item(), item);
        context.getVariables().put(config.index(), cursor);
        flattenItem(config.item(), item, context.getVariables());
        context.getVariables().put(cursorKey, cursor + 1);

        return NodeHandlerResult.of(config.body());
    }

    private LoopConfig readConfig(FlowStepEntity step) {
        if (step.getPreProcessor() == null || step.getPreProcessor().isBlank()) {
            return LoopConfig.defaults();
        }
        try {
            Map<String, Object> proc = jsonMapper.readValue(step.getPreProcessor(), new TypeReference<>() {
            });
            Object loopObj = proc.get("loop");
            if (!(loopObj instanceof Map<?, ?> loopMap)) {
                return LoopConfig.defaults();
            }
            String source = stringValue(loopMap.get("source"), "items");
            String item = stringValue(loopMap.get("item"), "item");
            String index = stringValue(loopMap.get("index"), "index");
            String body = stringValue(loopMap.get("body"), step.getNextIfFalse());
            int count = intValue(loopMap.get("count"), -1);
            return new LoopConfig(source, item, index, body, count);
        } catch (Exception e) {
            log.error("Failed to read loop config for step {}: {}", step.getId(), e.getMessage());
            return LoopConfig.defaults();
        }
    }

    private List<?> resolveValues(LoopConfig config, Map<String, Object> variables) {
        if (config.count() >= 0) {
            List<Integer> range = new ArrayList<>();
            for (int i = 0; i < config.count(); i++) {
                range.add(i);
            }
            return range;
        }

        Object source = variables.get(config.source());
        if (source instanceof List<?> list) {
            return list;
        }
        if (source instanceof Object[] array) {
            return List.of(array);
        }
        return List.of();
    }

    private int readCursor(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null && !String.valueOf(value).isBlank()) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException _) {
                return 0;
            }
        }
        return 0;
    }

    private void flattenItem(String itemName, Object item, Map<String, Object> variables) {
        List<Map.Entry<String, Object>> flat = new ArrayList<>();
        DataUtils.flattenObject(item, itemName, flat);
        for (Map.Entry<String, Object> entry : flat) {
            variables.put(entry.getKey(), entry.getValue());
        }
    }

    private void clearLoopVariables(LoopConfig config, Map<String, Object> variables) {
        variables.remove(config.item());
        variables.remove(config.index());
        String prefix = config.item() + ".";
        variables.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private String stringValue(Object value, String fallback) {
        return value != null && !String.valueOf(value).isBlank() ? String.valueOf(value) : fallback;
    }

    private int intValue(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    private record LoopConfig(String source, String item, String index, String body, int count) {
        static LoopConfig defaults() {
            return new LoopConfig("items", "item", "index", null, -1);
        }
    }
}
