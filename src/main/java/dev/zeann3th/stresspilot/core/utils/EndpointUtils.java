package dev.zeann3th.stresspilot.core.utils;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointCommand;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.commands.endpoint.EndpointResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

@Slf4j(topic = "EndpointUtils")
public class EndpointUtils {

    private EndpointUtils() {}

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    public static EndpointEntity merge(EndpointEntity stored, ExecuteEndpointCommand command) {
        if (command == null) return stored;

        EndpointEntity.EndpointEntityBuilder<?, ?> builder = stored.toBuilder();

        if (DataUtils.hasText(command.getUrl())) {
            builder.url(command.getUrl());
        }

        if (command.getHttpHeaders() != null && !command.getHttpHeaders().isEmpty()) {
            builder.httpHeaders(DataUtils.parseObjToJson(command.getHttpHeaders()));
        }

        if (command.getBody() != null) {
            builder.body(DataUtils.parseObjToString(command.getBody()));
        }

        if (command.getHttpParameters() != null && !command.getHttpParameters().isEmpty()) {
            builder.httpParameters(DataUtils.parseObjToJson(command.getHttpParameters()));
        }

        return builder.build();
    }

    public static void evaluateSuccessCondition(EndpointEntity endpoint, EndpointResponse response) {
        if (!response.isSuccess() || endpoint.getSuccessCondition() == null || endpoint.getSuccessCondition().isBlank()) {
            return;
        }
        try {
            StandardEvaluationContext context = new StandardEvaluationContext(response);

            Boolean result = PARSER.parseExpression(endpoint.getSuccessCondition())
                    .getValue(context, Boolean.class);

            if (result != null) {
                response.setSuccess(result);
                if (!result) {
                    response.setMessage("Condition failed: " + endpoint.getSuccessCondition());
                    response.setSuccess(false);
                }
            }
        } catch (Exception e) {
            log.warn("Eval failed: {}", e.getMessage());
            response.setSuccess(false);
            response.setMessage("Eval Error: " + e.getMessage());
        }
    }
}
