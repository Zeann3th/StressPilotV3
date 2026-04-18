package dev.zeann3th.stresspilot.core.services.flows.nodes.strategies;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.ports.store.FlowStepStore;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandler;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandlerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j(topic = "[SubFlowNodeHandler]")
@Component
@RequiredArgsConstructor
public class SubFlowNodeHandler implements FlowNodeHandler {

    private final FlowStepStore flowStepStore;

    private final ObjectProvider<FlowNodeHandlerFactory> nodeHandlerFactoryProvider;

    @Override
    public String getSupportedType() {
        return FlowStepType.SUBFLOW.name();
    }

    @Override
    public String handle(FlowStepEntity step, Map<String, FlowStepEntity> parentStepMap, FlowExecutionContext context) {
        Long subFlowId;
        try {
            subFlowId = Long.parseLong(step.getCondition());
        } catch (NumberFormatException _) {
            log.error("Invalid Sub-Flow ID in condition field: {}", step.getCondition());
            return step.getNextIfFalse();
        }

        List<FlowStepEntity> subSteps = flowStepStore.findAllByFlowIdWithEndpoint(subFlowId);
        if (subSteps.isEmpty()) {
            return step.getNextIfTrue();
        }

        Map<String, FlowStepEntity> subStepMap = subSteps.stream()
                .collect(Collectors.toMap(FlowStepEntity::getId, s -> s));

        FlowStepEntity current = subStepMap.values().stream()
                .filter(s -> FlowStepType.START.name().equalsIgnoreCase(s.getType()))
                .findFirst()
                .orElse(null);

        int jumpCount = 0;
        final int MAX_JUMPS = 10000;

        FlowNodeHandlerFactory nodeHandlerFactory = nodeHandlerFactoryProvider.getObject();

        while (current != null) {
            if (context.shouldStop()) {
                break;
            }

            if (jumpCount++ > MAX_JUMPS) {
                log.warn("Sub-flow {} exceeded max jumps!", subFlowId);
                break;
            }

            String type = current.getType().toUpperCase();

            String nextId = nodeHandlerFactory.getHandler(type).handle(current, subStepMap, context);

            current = nextId != null ? subStepMap.get(nextId) : null;
        }

        return step.getNextIfTrue();
    }
}
