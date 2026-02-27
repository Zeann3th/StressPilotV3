package dev.zeann3th.stresspilot.infrastructure.adapters.message;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.ports.message.RequestMessagePort;
import dev.zeann3th.stresspilot.infrastructure.configs.properties.RequestLogWriterProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "[WS-LogWriter]")
@Component
@ConditionalOnProperty(prefix = "stresspilot.message.websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class WebSocketRequestMessagePort implements RequestMessagePort {

    private final DatabaseRequestMessagePort dbWriter;
    private final SimpMessagingTemplate messagingTemplate;
    private final RequestLogWriterProperties properties;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "ws-log-pusher")
    );

    @PostConstruct
    public void start() {
        long interval = properties.getWebsocket().getPushIntervalMs();
        scheduler.scheduleAtFixedRate(this::push, interval, interval, TimeUnit.MILLISECONDS);
        log.info("WebSocketRequestLogWriter started (topic={}, pushIntervalMs={})",
                properties.getWebsocket().getTopic(), interval);
    }


    @Override
    public void write(RequestLogEntity log) { /* delegated via wsBuffer */ }

    @Override
    public void writeAll(List<RequestLogEntity> logs) { /* delegated via wsBuffer */ }

    @Override
    public void flush() { /* best-effort, no blocking */ }

    private void push() {
        if (dbWriter.wsBuffer.isEmpty()) return;

        List<RequestLogEntity> batch = new ArrayList<>();
        RequestLogEntity item;
        int limit = properties.getWebsocket().getMaxPerPush();
        while (batch.size() < limit && (item = dbWriter.wsBuffer.poll()) != null) {
            batch.add(item);
        }

        if (!batch.isEmpty()) {
            try {
                messagingTemplate.convertAndSend(properties.getWebsocket().getTopic(), batch);
            } catch (Exception e) {
                log.warn("Failed to push {} logs to WebSocket: {}", batch.size(), e.getMessage());
            }
        }
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
    }
}
