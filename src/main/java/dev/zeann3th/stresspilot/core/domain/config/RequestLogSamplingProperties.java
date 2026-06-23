package dev.zeann3th.stresspilot.core.domain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.message.sampling")
public class RequestLogSamplingProperties {
    private boolean enabled = false;
    private double rate = 1.0;
}
