package dev.zeann3th.stresspilot.infrastructure.adapters.message;

import dev.zeann3th.stresspilot.core.domain.entities.MetricScrapeEventEntity;
import dev.zeann3th.stresspilot.core.ports.message.MetricMessagePort;
import dev.zeann3th.stresspilot.core.ports.store.MetricScrapeEventStore;
import dev.zeann3th.stresspilot.infrastructure.configs.properties.RequestLogWriterProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j(topic = "[DB-MetricWriter]")
@Component
@RequiredArgsConstructor
public class DatabaseMetricMessagePort implements MetricMessagePort {

    private static final int QUEUE_CAPACITY = 50_000;
    private static final int MAX_RETRIES = 3;

    private final MetricScrapeEventStore metricScrapeEventStore;
    private final TransactionTemplate transactionTemplate;
    private final RequestLogWriterProperties properties;

    private final BlockingQueue<MetricScrapeEventEntity> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition flushDone = lock.newCondition();

    @PostConstruct
    public void start() {
        Thread writer = new Thread(this::writerLoop, "metric-writer");
        writer.setDaemon(true);
        writer.start();
        log.info("DatabaseMetricWriter started (batchSize={}, flushIntervalMs={})",
                properties.getDatasource().getBatchSize(), properties.getDatasource().getFlushIntervalMs());
    }

    @Override
    public void write(MetricScrapeEventEntity entry) {
        if (!queue.offer(entry)) {
            log.warn("Metric queue full ({}) — entry dropped", queue.size());
        }
    }

    @Override
    public void flush() {
        long nanos = TimeUnit.SECONDS.toNanos(5);
        lock.lock();
        try {
            while ((!queue.isEmpty() || flushing.get()) && nanos > 0) {
                nanos = flushDone.awaitNanos(nanos);
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for metric flush");
        } finally {
            lock.unlock();
        }
    }

    private void writerLoop() {
        List<MetricScrapeEventEntity> buffer = new ArrayList<>(properties.getDatasource().getBatchSize());
        long lastFlush = System.currentTimeMillis();

        while (running.get() || !queue.isEmpty() || !buffer.isEmpty()) {
            try {
                long now = System.currentTimeMillis();
                long elapsed = now - lastFlush;
                long waitMs = Math.max(0, properties.getDatasource().getFlushIntervalMs() - elapsed);

                MetricScrapeEventEntity entry = queue.poll(waitMs, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    buffer.add(entry);
                    queue.drainTo(buffer, properties.getDatasource().getBatchSize() - 1);
                }

                now = System.currentTimeMillis();
                boolean full = buffer.size() >= properties.getDatasource().getBatchSize();
                boolean timeout = (now - lastFlush) >= properties.getDatasource().getFlushIntervalMs();
                boolean shutdown = !running.get() && !buffer.isEmpty();

                if ((full || timeout || shutdown) && !buffer.isEmpty()) {
                    flushing.set(true);
                    List<MetricScrapeEventEntity> batch = new ArrayList<>(buffer);
                    persistBatch(batch);
                    buffer.clear();
                    lastFlush = System.currentTimeMillis();
                    flushing.set(false);
                    signalFlushDone();
                }

                if (!running.get() && queue.isEmpty() && buffer.isEmpty())
                    break;

            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Unexpected error in metric-writer thread", e);
            }
        }
    }

    private void persistBatch(List<MetricScrapeEventEntity> batch) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(_ -> metricScrapeEventStore.saveAll(batch));
                log.debug("Flushed {} metrics to DB", batch.size());
                return;
            } catch (Exception e) {
                log.warn("DB metric flush attempt {}/{} failed (batch={}): {}", attempt, MAX_RETRIES, batch.size(), e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(100L * attempt);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.error("Dropped {} metric entries after {} failed flush attempts", batch.size(), MAX_RETRIES);
    }

    private void signalFlushDone() {
        lock.lock();
        try {
            flushDone.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
    }
}
