package dev.zeann3th.stresspilot.core.services.parsers;

import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import org.pf4j.ExtensionPoint;

import java.util.List;

public interface ParserService extends ExtensionPoint {
    String getType();

    List<EndpointEntity> parse(String spec);
}
