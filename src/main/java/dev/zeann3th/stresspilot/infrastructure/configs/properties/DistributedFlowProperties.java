package dev.zeann3th.stresspilot.infrastructure.configs.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "application.distributed")
public class DistributedFlowProperties {
    private boolean enabled = false;
    private String keyPrefix = "stresspilot";
    private String nodeId = java.util.UUID.randomUUID().toString();
    private int workerTtlSeconds = 15;
    private int workerHeartbeatSeconds = 5;
    private int workerDiscoveryTimeoutMs = 2000;
}
