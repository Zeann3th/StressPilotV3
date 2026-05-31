package dev.zeann3th.stresspilot.infrastructure.configs.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Data
@ConfigurationProperties(prefix = "application.distributed")
public class DistributedFlowProperties {
    private boolean enabled = false;
    private String keyPrefix = "stresspilot";
    private String nodeId = resolveDefaultNodeId(System.getenv());
    private int workerTtlSeconds = 15;
    private int workerHeartbeatSeconds = 5;
    private int workerDiscoveryTimeoutMs = 2000;

    public static String resolveDefaultNodeId(Map<String, String> environment) {
        String hostName = environment.get("HOSTNAME");
        if (hostName != null && !hostName.isBlank()) {
            return hostName;
        }

        String computerName = environment.get("COMPUTERNAME");
        if (computerName != null && !computerName.isBlank()) {
            return computerName;
        }

        return "local";
    }
}
