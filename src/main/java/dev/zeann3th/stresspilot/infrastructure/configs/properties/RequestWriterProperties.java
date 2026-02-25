package dev.zeann3th.stresspilot.infrastructure.configs.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.request-writer")
@Getter
@Setter
public class RequestWriterProperties {
    private int batchSize = 50;
    private long flushIntervalMs = 500;
}
