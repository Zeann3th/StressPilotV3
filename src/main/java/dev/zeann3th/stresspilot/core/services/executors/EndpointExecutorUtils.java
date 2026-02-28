package dev.zeann3th.stresspilot.core.services.executors;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

@Slf4j(topic = "[EndpointExecutorUtils]")
@UtilityClass
public class EndpointExecutorUtils {

    private static final ExpressionParser SPEL = new SpelExpressionParser();

    public static void evaluateSuccessCondition(EndpointEntity endpoint, ExecuteEndpointResponse response) {
        if (!response.isSuccess())
            return;
        String condition = endpoint.getSuccessCondition();
        if (condition == null || condition.isBlank())
            return;

        try {
            StandardEvaluationContext ctx = new StandardEvaluationContext(response);
            Boolean result = SPEL.parseExpression(condition).getValue(ctx, Boolean.class);
            if (result != null) {
                response.setSuccess(result);
                if (!result)
                    response.setMessage("Condition failed: " + condition);
            }
        } catch (Exception e) {
            log.warn("Success condition eval error: {}", e.getMessage());
            response.setSuccess(false);
            response.setMessage("Eval Error: " + e.getMessage());
        }
    }
}
