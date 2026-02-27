package dev.zeann3th.stresspilot.core.services.executors;

import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import lombok.RequiredArgsConstructor;
import org.pf4j.PluginManager;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EndpointExecutorServiceFactory {
    private final List<EndpointExecutorService> executors;

    private final PluginManager pluginManager;

    public EndpointExecutorService getExecutor(String type) {
        var internal = executors.stream()
                .filter(executor -> executor.getType().equalsIgnoreCase(type))
                .findFirst();

        if (internal.isPresent()) {
            return internal.get();
        }

        List<EndpointExecutorService> extensions = pluginManager.getExtensions(EndpointExecutorService.class);
        return extensions.stream()
                .filter(executor -> executor.getType().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0009));
    }
}
