package dev.zeann3th.stresspilot.infrastructure.configs;

import dev.zeann3th.stresspilot.infrastructure.configs.properties.DistributedFlowProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DistributedFlowProperties.class)
public class DistributedFlowPropertiesConfig {
}
