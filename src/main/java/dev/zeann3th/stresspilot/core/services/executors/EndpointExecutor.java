package dev.zeann3th.stresspilot.core.services.executors;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.ExecuteEndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.services.executors.context.ExecutionContext;
import org.pf4j.ExtensionPoint;

import java.util.Map;

public interface EndpointExecutor extends ExtensionPoint {
    String getType();

    ExecuteEndpointResponse execute(
            EndpointEntity endpoint,
            Map<String, Object> environment,
            ExecutionContext context
    );
}
