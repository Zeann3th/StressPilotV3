package dev.zeann3th.stresspilot.core.services.flows.nodes.strategies;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.services.flows.FlowExecutionContext;
import dev.zeann3th.stresspilot.core.services.flows.nodes.FlowNodeHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.MapAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j(topic = "[BranchNodeHandler]")
@Component
@RequiredArgsConstructor
public class BranchNodeHandler implements FlowNodeHandler {

    private static final SpelExpressionParser SPEL = new SpelExpressionParser();

    @Override
    public String getSupportedType() {
        return FlowStepType.BRANCH.name();
    }

    @Override
    public String handle(FlowStepEntity step, Map<String, FlowStepEntity> stepMap, FlowExecutionContext context) {
        boolean result = evaluateCondition(step.getCondition(), context.getVariables());
        log.debug("Thread {} branch condition='{}' -> {}", context.getThreadId(), step.getCondition(), result);
        return result ? step.getNextIfTrue() : step.getNextIfFalse();
    }

    public static boolean evaluateCondition(String condition, Map<String, Object> variables) {
        if (condition == null || condition.isBlank()) return false;
        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext(variables);
            ctx.addPropertyAccessor(new MapAccessor());
            return Boolean.TRUE.equals(SPEL.parseExpression(condition).getValue(ctx, Boolean.class));
        } catch (Exception e) {
            log.error("Error evaluating condition '{}': {}", condition, e.getMessage());
            return false;
        }
    }
}
