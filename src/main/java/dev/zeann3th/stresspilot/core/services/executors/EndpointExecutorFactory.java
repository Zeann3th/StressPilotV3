package dev.zeann3th.stresspilot.core.services.executors;

import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import lombok.RequiredArgsConstructor;
import org.pf4j.PluginManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class EndpointExecutorFactory {
    private final List<EndpointExecutor> executors;

    private final PluginManager pluginManager;

    public EndpointExecutor getExecutor(String type) {
        var internal = executors.stream()
                .filter(executor -> executor.getType().equalsIgnoreCase(type))
                .findFirst();

        if (internal.isPresent()) {
            return internal.get();
        }

        List<EndpointExecutor> extensions = pluginManager.getExtensions(EndpointExecutor.class);
        return extensions.stream()
                .filter(executor -> executor.getType().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0009));
    }

    public List<String> listTypes() {
            List<EndpointExecutor> extensions = pluginManager.getExtensions(EndpointExecutor.class);
            return Stream.concat(executors.stream(), extensions.stream())
                    .map(EndpointExecutor::getType)
                    .collect(Collectors.toList());
    }
}
