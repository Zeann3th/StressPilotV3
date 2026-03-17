package dev.zeann3th.stresspilot.core.services.parsers.endpoints;

import dev.zeann3th.stresspilot.core.domain.commands.ParserCapability;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import org.pf4j.ExtensionPoint;

import java.util.List;

public interface ParserService extends ExtensionPoint {
    String getType();

    List<String> getSupportedFormats();

    boolean supports(String filename, String contentType, String content);

    List<EndpointEntity> unmarshal(String spec);

    default ParserCapability getCapability() {
        return new ParserCapability(getType(), getSupportedFormats());
    }
}
