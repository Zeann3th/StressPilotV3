package dev.zeann3th.stresspilot.infrastructure.configs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "application.distributed", name = "enabled", havingValue = "true")
public class DistributedRedisConfig {
}
