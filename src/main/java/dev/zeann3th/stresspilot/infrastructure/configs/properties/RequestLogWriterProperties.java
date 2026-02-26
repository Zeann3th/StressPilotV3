package dev.zeann3th.stresspilot.infrastructure.configs.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "stresspilot.message")
public class RequestLogWriterProperties {

    /** Number of logs to accumulate before a forced DB flush. */
    private int batchSize = 1000;

    /** Max time (ms) between DB flushes even if batch is not full. */
    private long flushIntervalMs = 1000;

    /** WebSocket push config. */
    private Websocket websocket = new Websocket();

    /** Kafka push config. */
    private Kafka kafka = new Kafka();

    @Data
    public static class Websocket {
        private boolean enabled = true;
        /** Push interval in ms. */
        private long pushIntervalMs = 500;
        /** Max logs sent per push cycle. */
        private int maxPerPush = 2000;
        /** Topic to push to. */
        private String topic = "/topic/logs";
    }

    @Data
    public static class Kafka {
        private boolean enabled = false;
        private String topic = "stresspilot.request-logs";
        private String bootstrapServers = "localhost:9092";
    }
}
