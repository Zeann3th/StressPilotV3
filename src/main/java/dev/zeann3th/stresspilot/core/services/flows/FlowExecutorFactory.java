package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import lombok.RequiredArgsConstructor;
import org.pf4j.PluginManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class FlowExecutorFactory {

    private final List<FlowExecutor> strategies;
    private final PluginManager pluginManager;

    public FlowExecutor getStrategy(String type) {
        Optional<FlowExecutor> internal = strategies.stream()
                .filter(strategy -> strategy.supports(type))
                .findFirst();

        if (internal.isPresent()) {
            return internal.get();
        }

        List<FlowExecutor> extensions = pluginManager.getExtensions(FlowExecutor.class);
        return extensions.stream()
                .filter(strategy -> strategy.supports(type))
                .findFirst()
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0020,
                        java.util.Map.of("reason", "Unsupported flow type: " + type)));
    }

    public List<String> listTypes() {
        List<FlowExecutor> extensions = pluginManager.getExtensions(FlowExecutor.class);
        return Stream.concat(strategies.stream(), extensions.stream())
                .map(FlowExecutor::getType)
                .collect(java.util.stream.Collectors.toList());
    }
}
