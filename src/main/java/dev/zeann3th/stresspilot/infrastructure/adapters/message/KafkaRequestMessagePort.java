package dev.zeann3th.stresspilot.infrastructure.adapters.message;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.ports.message.RequestMessagePort;
import dev.zeann3th.stresspilot.infrastructure.configs.properties.RequestLogWriterProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j(topic = "[Kafka-LogWriter]")
@Component
@ConditionalOnProperty(prefix = "stresspilot.message.kafka", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class KafkaRequestMessagePort implements RequestMessagePort {

    private final RequestLogWriterProperties properties;

    @Override
    public void write(RequestLogEntity logEntry) {
        log.debug("[KAFKA STUB] Would send log id={} to topic {}",
                logEntry.getId(), properties.getKafka().getTopic());
        // TODO: kafkaTemplate.send(properties.getKafka().getTopic(), logEntry);
    }

    @Override
    public void writeAll(List<RequestLogEntity> logs) {
        logs.forEach(this::write);
    }

    @Override
    public void flush() {
        // TODO: kafkaTemplate.flush();
    }
}
