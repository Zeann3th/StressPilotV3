package dev.zeann3th.stresspilot.core.services.flows.nodes;

import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class FlowNodeHandlerFactory {

    private final List<FlowNodeHandler> handlers;

    private Map<String, FlowNodeHandler> handlerMap;

    @jakarta.annotation.PostConstruct
    public void init() {
        handlerMap = handlers.stream()
                .collect(Collectors.toMap(FlowNodeHandler::getSupportedType, Function.identity()));
    }

    public FlowNodeHandler getHandler(String type) {
        FlowNodeHandler handler = handlerMap.get(type);
        if (handler == null) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0021,
                    Map.of(Constants.TYPE, type));
        }
        return handler;
    }
}
