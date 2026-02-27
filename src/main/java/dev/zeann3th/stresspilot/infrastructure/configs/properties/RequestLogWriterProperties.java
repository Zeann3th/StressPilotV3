package dev.zeann3th.stresspilot.infrastructure.configs.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.message")
public class RequestLogWriterProperties {

    private int batchSize = 1000;

    private long flushIntervalMs = 1000;

    /** WebSocket push config. */
    private Websocket websocket = new Websocket();

    /** Kafka push config. */
    private Kafka kafka = new Kafka();

    @Data
    public static class Websocket {
        private boolean enabled = true;
        private long pushIntervalMs = 500;
        private int maxPerPush = 2000;
        private String topic = "/topic/logs";
    }

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String topic = "stresspilot.request-logs";
        private String bootstrapServers = "localhost:9092";
    }
}
