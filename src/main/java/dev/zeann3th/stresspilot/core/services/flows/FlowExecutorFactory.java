package dev.zeann3th.stresspilot.core.services.flows;

import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentVariableStore;
import dev.zeann3th.stresspilot.core.ports.store.ProjectStore;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.services.ActiveRunRegistry;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import lombok.RequiredArgsConstructor;
import org.pf4j.PluginManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class FlowExecutorFactory {

    private final List<FlowExecutor> strategies;
    private final PluginManager pluginManager;
    private final ProjectStore projectStore;
    private final EnvironmentVariableStore envVarStore;
    private final RunStore runStore;
    private final ActiveRunRegistry activeRunRegistry;
    private final RequestLogService requestLogService;

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
                .map(ext -> {
                    ext.initInfra(projectStore, envVarStore, runStore, activeRunRegistry, requestLogService);
                    return ext;
                })
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0020,
                        java.util.Map.of("reason", "Unsupported flow type: " + type)));
    }

    public List<String> listTypes() {
        List<FlowExecutor> extensions = pluginManager.getExtensions(FlowExecutor.class);
        return Stream.concat(strategies.stream(), extensions.stream())
                .map(FlowExecutor::getType)
                .collect(Collectors.toList());
    }
}