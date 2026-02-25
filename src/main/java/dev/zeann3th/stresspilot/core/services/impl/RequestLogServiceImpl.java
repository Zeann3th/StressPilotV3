package dev.zeann3th.stresspilot.core.services.impl;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.ports.store.RequestLogStore;
import dev.zeann3th.stresspilot.core.services.RequestLogService;
import dev.zeann3th.stresspilot.core.domain.commands.report.RequestLogResponse;
import dev.zeann3th.stresspilot.infrastructure.configs.properties.RequestWriterProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestLogServiceImpl implements RequestLogService {

    private final TransactionTemplate transactionTemplate;
    private final RequestLogStore requestLogStore;
    private final SimpMessagingTemplate messagingTemplate;
    private final RequestWriterProperties properties;

    private final BlockingQueue<RequestLogEntity> queue = new LinkedBlockingQueue<>(200_000);

    private final Queue<RequestLogEntity> websocketBuffer = new ConcurrentLinkedQueue<>();

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean isFlushing = new AtomicBoolean(false);

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition flushCondition = lock.newCondition();

    private final ScheduledExecutorService websocketScheduler = Executors.newSingleThreadScheduledExecutor();

    private int batchSize;
    private long flushIntervalMs;
    private static final int MAX_WS_BUFFER_SIZE = 5000;

    @PostConstruct
    public void init() {
        this.batchSize = properties.getBatchSize() > 0 ? properties.getBatchSize() : 1000;
        this.flushIntervalMs = properties.getFlushIntervalMs() > 0 ? properties.getFlushIntervalMs() : 1000;

        startWriter();
        startWebsocketPusher();
    }

    private void startWriter() {
        Thread writer = new Thread(this::writerLoop, "request-log-writer");
        writer.setDaemon(true);
        writer.start();
    }

    private void startWebsocketPusher() {
        websocketScheduler.scheduleAtFixedRate(this::pushWebsocketUpdates, 500, 500, TimeUnit.MILLISECONDS);
    }

    private void writerLoop() {
        List<RequestLogEntity> buffer = new ArrayList<>(batchSize);
        long lastFlushTime = System.currentTimeMillis();

        while (running.get() || !queue.isEmpty() || !buffer.isEmpty()) {
            try {
                long now = System.currentTimeMillis();
                long timeSinceLastFlush = now - lastFlushTime;
                long waitTime = Math.max(0, flushIntervalMs - timeSinceLastFlush);

                RequestLogEntity logEntry = queue.poll(waitTime, TimeUnit.MILLISECONDS);

                if (logEntry != null) {
                    buffer.add(logEntry);
                    queue.drainTo(buffer, batchSize - 1);
                }

                now = System.currentTimeMillis();
                boolean batchFull = buffer.size() >= batchSize;
                boolean timeoutReached = (now - lastFlushTime) >= flushIntervalMs;
                boolean shuttingDownAndBufferHasData = !running.get() && !buffer.isEmpty();

                if ((batchFull || timeoutReached || shuttingDownAndBufferHasData) && !buffer.isEmpty()) {
                    isFlushing.set(true);

                    List<RequestLogEntity> batchToSave = new ArrayList<>(buffer);
                    flush(batchToSave);

                    if (websocketBuffer.size() < MAX_WS_BUFFER_SIZE) {
                        websocketBuffer.addAll(batchToSave);
                    }

                    buffer.clear();
                    lastFlushTime = System.currentTimeMillis();
                    isFlushing.set(false);

                    signalFlushComplete();
                }

                if (!running.get() && queue.isEmpty() && buffer.isEmpty()) {
                    break;
                }

            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in log writer thread", e);
            }
        }
    }

    private void flush(List<RequestLogEntity> buffer) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                transactionTemplate.executeWithoutResult(_ -> requestLogStore.saveAll(buffer));
                return;
            } catch (Exception _) {
                log.warn("DB flush failed. Retrying... ({}/{})", i + 1, maxRetries);
                try {
                    Thread.sleep(100 * (i + 1));
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("Failed to save batch of {} logs after retries. Data dropped.", buffer.size());
    }

    private void pushWebsocketUpdates() {
        if (websocketBuffer.isEmpty()) return;

        List<RequestLogEntity> logsToSend = new ArrayList<>();
        RequestLogEntity item;

        int count = 0;
        while (count < 2000 && (item = websocketBuffer.poll()) != null) {
            logsToSend.add(item);
            count++;
        }

        if (!logsToSend.isEmpty()) {
            try {
                List<RequestLogResponse> lightLogs = logsToSend.stream()
                        .map(l -> RequestLogResponse.builder()
                                .id(l.getId())
                                .endpointId(l.getEndpointId())
                                .statusCode(l.getStatusCode())
                                .responseTime(l.getResponseTime())
                                .request(l.getRequest())
                                .response(l.getResponse())
                                .createdAt(l.getCreatedAt())
                                .build())
                        .toList();
                messagingTemplate.convertAndSend("/topic/logs", lightLogs);
            } catch (Exception e) {
                log.warn("Failed to push logs to websocket: {}", e.getMessage());
            }
        }
    }

    private void signalFlushComplete() {
        lock.lock();
        try {
            flushCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void queueLog(RequestLogEntity logEntity) {
        if (!queue.offer(logEntity) && log.isDebugEnabled()) {
                log.warn("Log queue is full (size: {}) - dropping log entry", queue.size());
            }

    }

    @Override
    public void ensureFlushed() {
        long nanos = TimeUnit.SECONDS.toNanos(5);
        lock.lock();
        try {
            while ((!queue.isEmpty() || isFlushing.get()) && nanos > 0) {
                nanos = flushCondition.awaitNanos(nanos);
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for logs to flush");
        } finally {
            lock.unlock();
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        websocketScheduler.shutdown();
        try {
            if (!websocketScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                websocketScheduler.shutdownNow();
            }
        } catch (InterruptedException _) {
            websocketScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
