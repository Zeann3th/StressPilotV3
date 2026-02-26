package dev.zeann3th.stresspilot.core.services.parsers;

import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;

import java.util.List;

public interface ParserService {
    String getType();
    List<EndpointEntity> parse(String spec);
}
