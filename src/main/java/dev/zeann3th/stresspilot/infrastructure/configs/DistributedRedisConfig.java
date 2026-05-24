package dev.zeann3th.stresspilot.infrastructure.configs;

import dev.zeann3th.stresspilot.infrastructure.configs.properties.DistributedFlowProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DistributedFlowProperties.class)
@ConditionalOnProperty(prefix = "application.distributed", name = "enabled", havingValue = "true")
public class DistributedRedisConfig {
}
