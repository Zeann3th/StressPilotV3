package dev.zeann3th.stresspilot.core.services.flows.nodes;

import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.enums.FlowStepType;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory that selects the correct {@link FlowNodeHandler} for a given step type.
 *
 * <p>All handlers are Spring-injected; no manual registration required.
 * Unknown types throw {@link dev.zeann3th.stresspilot.core.domain.exception.CommandException}.</p>
 */
@Component
@RequiredArgsConstructor
public class FlowNodeHandlerFactory {

    private final List<FlowNodeHandler> handlers;

    private Map<FlowStepType, FlowNodeHandler> handlerMap;

    @jakarta.annotation.PostConstruct
    public void init() {
        handlerMap = handlers.stream()
                .collect(Collectors.toMap(FlowNodeHandler::getSupportedType, Function.identity()));
    }

    public FlowNodeHandler getHandler(FlowStepType type) {
        FlowNodeHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw CommandExceptionBuilder.exception(ErrorCode.SP0001,
                    Map.of("reason", "No handler registered for flow step type: " + type));
        }
        return handler;
    }
}
