package dev.zeann3th.stresspilot.core.services.executors;

import dev.zeann3th.stresspilot.core.domain.commands.endpoint.EndpointResponse;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import okhttp3.CookieJar;
import org.pf4j.ExtensionPoint;

import java.util.Map;

public interface EndpointExecutorService extends ExtensionPoint {
    String getType();

    EndpointResponse execute(EndpointEntity endpointEntity, Map<String, Object> environment, CookieJar cookieJar);
}
