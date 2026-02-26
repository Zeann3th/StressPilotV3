package dev.zeann3th.stresspilot.infrastructure.adapters.message;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.ports.message.RequestMessagePort;
import dev.zeann3th.stresspilot.core.ports.store.RequestLogStore;
import dev.zeann3th.stresspilot.infrastructure.configs.properties.RequestLogWriterProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Batched, async write-ahead writer that persists request logs to the database.
 *
 * <p>Design:
 * <ul>
 *   <li>A single background {@code request-log-writer} thread drains an in-memory
 *       {@link BlockingQueue}.</li>
 *   <li>DB writes are batched (configurable {@code batchSize} / {@code flushIntervalMs}).</li>
 *   <li>On failure it retries up to 3 times with exponential back-off before dropping.</li>
 *   <li>{@link #flush()} blocks until all queued entries are persisted — called at run end.</li>
 * </ul>
 * </p>
 */
@Slf4j(topic = "[DB-LogWriter]")
@Component
@RequiredArgsConstructor
public class DatabaseRequestMessagePort implements RequestMessagePort {

    private static final int QUEUE_CAPACITY = 200_000;
    private static final int MAX_RETRIES = 3;

    private final RequestLogStore requestLogStore;
    private final TransactionTemplate transactionTemplate;
    private final RequestLogWriterProperties properties;

    private final BlockingQueue<RequestLogEntity> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean running   = new AtomicBoolean(true);
    private final AtomicBoolean flushing  = new AtomicBoolean(false);
    private final ReentrantLock lock      = new ReentrantLock();
    private final Condition flushDone     = lock.newCondition();

    // Package-visible so WebSocketRequestLogWriter can read from the same writes
    final ConcurrentLinkedQueue<RequestLogEntity> wsBuffer = new ConcurrentLinkedQueue<>();

    @PostConstruct
    public void start() {
        Thread writer = new Thread(this::writerLoop, "request-log-writer");
        writer.setDaemon(true);
        writer.start();
        log.info("DatabaseRequestLogWriter started (batchSize={}, flushIntervalMs={})",
                properties.getBatchSize(), properties.getFlushIntervalMs());
    }

    // ─── RequestLogWriter ────────────────────────────────────────────────────

    @Override
    public void write(RequestLogEntity entry) {
        if (!queue.offer(entry)) {
            log.warn("Log queue full ({}) — entry dropped", queue.size());
        }
    }

    @Override
    public void writeAll(List<RequestLogEntity> logs) {
        logs.forEach(this::write);
    }

    @Override
    public void flush() {
        long nanos = TimeUnit.SECONDS.toNanos(5);
        lock.lock();
        try {
            while ((!queue.isEmpty() || flushing.get()) && nanos > 0) {
                nanos = flushDone.awaitNanos(nanos);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for log flush");
        } finally {
            lock.unlock();
        }
    }

    // ─── Internal ────────────────────────────────────────────────────────────

    private void writerLoop() {
        List<RequestLogEntity> buffer = new ArrayList<>(properties.getBatchSize());
        long lastFlush = System.currentTimeMillis();

        while (running.get() || !queue.isEmpty() || !buffer.isEmpty()) {
            try {
                long now             = System.currentTimeMillis();
                long elapsed         = now - lastFlush;
                long waitMs          = Math.max(0, properties.getFlushIntervalMs() - elapsed);

                RequestLogEntity entry = queue.poll(waitMs, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    buffer.add(entry);
                    queue.drainTo(buffer, properties.getBatchSize() - 1);
                }

                now = System.currentTimeMillis();
                boolean full     = buffer.size() >= properties.getBatchSize();
                boolean timeout  = (now - lastFlush) >= properties.getFlushIntervalMs();
                boolean shutdown = !running.get() && !buffer.isEmpty();

                if ((full || timeout || shutdown) && !buffer.isEmpty()) {
                    flushing.set(true);
                    List<RequestLogEntity> batch = new ArrayList<>(buffer);
                    persistBatch(batch);
                    wsBuffer.addAll(batch);
                    buffer.clear();
                    lastFlush = System.currentTimeMillis();
                    flushing.set(false);
                    signalFlushDone();
                }

                if (!running.get() && queue.isEmpty() && buffer.isEmpty()) break;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Unexpected error in log-writer thread", e);
            }
        }
    }

    private void persistBatch(List<RequestLogEntity> batch) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(_ -> requestLogStore.saveAll(batch));
                return;
            } catch (Exception e) {
                log.warn("DB flush attempt {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(100L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.error("Dropped {} log entries after {} failed flush attempts", batch.size(), MAX_RETRIES);
    }

    private void signalFlushDone() {
        lock.lock();
        try { flushDone.signalAll(); } finally { lock.unlock(); }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
    }
}
