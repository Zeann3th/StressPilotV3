package dev.zeann3th.stresspilot.core.services.flows.strategies;

import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import lombok.RequiredArgsConstructor;
import org.pf4j.PluginManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FlowExecutionStrategyFactory {

    private final List<FlowExecutionStrategy> strategies;
    private final PluginManager pluginManager;

    public FlowExecutionStrategy getStrategy(String type) {
        Optional<FlowExecutionStrategy> internal = strategies.stream()
                .filter(strategy -> strategy.supports(type))
                .findFirst();

        if (internal.isPresent()) {
            return internal.get();
        }

        List<FlowExecutionStrategy> extensions = pluginManager.getExtensions(FlowExecutionStrategy.class);
        return extensions.stream()
                .filter(strategy -> strategy.supports(type))
                .findFirst()
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0020,
                        java.util.Map.of("reason", "Unsupported flow type: " + type)));
    }
}
