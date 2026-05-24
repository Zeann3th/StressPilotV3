package dev.zeann3th.stresspilot.infrastructure.adapters.message;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.ports.message.RequestMessagePort;
import dev.zeann3th.stresspilot.core.ports.store.RequestLogStore;
import dev.zeann3th.stresspilot.infrastructure.configs.properties.RequestLogWriterProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j(topic = "[DB-LogWriter]")
@Component
public class DatabaseRequestMessagePort implements RequestMessagePort {

    private final RequestLogStore requestLogStore;
    private final TransactionTemplate transactionTemplate;
    private final RequestLogWriterProperties properties;

    private final BlockingQueue<RequestLogEntity> queue;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final AtomicBoolean flushRequested = new AtomicBoolean(false);
    private final AtomicInteger pendingEntries = new AtomicInteger();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition flushDone = lock.newCondition();

    public DatabaseRequestMessagePort(
            RequestLogStore requestLogStore,
            TransactionTemplate transactionTemplate,
            RequestLogWriterProperties properties) {
        this.requestLogStore = requestLogStore;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.queue = new LinkedBlockingQueue<>(properties.getDatasource().getQueueCapacity());
    }

    @PostConstruct
    public void start() {
        Thread writer = new Thread(this::writerLoop, "request-log-writer");
        writer.setDaemon(true);
        writer.start();
        log.info("DatabaseRequestLogWriter started (queueCapacity={}, batchSize={}, flushIntervalMs={})",
                properties.getDatasource().getQueueCapacity(),
                properties.getDatasource().getBatchSize(),
                properties.getDatasource().getFlushIntervalMs());
    }

    @Override
    public void write(RequestLogEntity entry) {
        if (offer(entry)) {
            pendingEntries.incrementAndGet();
        } else {
            log.warn("Log queue full ({}) — entry dropped", queue.size());
        }
    }

    private boolean offer(RequestLogEntity entry) {
        if (queue.offer(entry)) {
            return true;
        }
        try {
            return queue.offer(entry, properties.getDatasource().getOfferTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void flush() {
        flushRequested.set(true);
        long nanos = TimeUnit.SECONDS.toNanos(5);
        lock.lock();
        try {
            while ((pendingEntries.get() > 0 || flushing.get()) && nanos > 0) {
                nanos = flushDone.awaitNanos(nanos);
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for log flush");
        } finally {
            lock.unlock();
        }
    }

    private void writerLoop() {
        List<RequestLogEntity> buffer = new ArrayList<>(properties.getDatasource().getBatchSize());
        long lastFlush = System.currentTimeMillis();

        while (running.get() || !queue.isEmpty() || !buffer.isEmpty()) {
            try {
                long now = System.currentTimeMillis();
                long elapsed = now - lastFlush;
                long waitMs = Math.max(0, properties.getDatasource().getFlushIntervalMs() - elapsed);

                RequestLogEntity entry = queue.poll(waitMs, TimeUnit.MILLISECONDS);
                if (entry != null) {
                    buffer.add(entry);
                    int remainingBatchCapacity = properties.getDatasource().getBatchSize() - buffer.size();
                    if (remainingBatchCapacity > 0) {
                        queue.drainTo(buffer, remainingBatchCapacity);
                    }
                }

                now = System.currentTimeMillis();
                boolean full = buffer.size() >= properties.getDatasource().getBatchSize();
                boolean timeout = (now - lastFlush) >= properties.getDatasource().getFlushIntervalMs();
                boolean requested = flushRequested.getAndSet(false);
                boolean shutdown = !running.get() && !buffer.isEmpty();

                if ((full || timeout || requested || shutdown) && !buffer.isEmpty()) {
                    flushing.set(true);
                    List<RequestLogEntity> batch = new ArrayList<>(buffer);
                    persistBatch(batch);
                    pendingEntries.addAndGet(-batch.size());
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
                log.error("Unexpected error in log-writer thread", e);
            }
        }
    }

    private void persistBatch(List<RequestLogEntity> batch) {
        int maxRetries = Math.max(1, properties.getDatasource().getMaxRetries());
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                transactionTemplate.executeWithoutResult(_ -> requestLogStore.saveAll(batch));
                log.debug("Flushed {} logs to DB", batch.size());
                return;
            } catch (Exception e) {
                log.warn("DB flush attempt {}/{} failed (batch={}): {}", attempt, maxRetries, batch.size(), e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(properties.getDatasource().getRetryBaseDelayMs() * attempt);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.error("Dropped {} log entries after {} failed flush attempts", batch.size(), maxRetries);
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
