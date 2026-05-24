package dev.zeann3th.stresspilot.infrastructure.adapters.message;

import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.ports.store.RequestLogStore;
import dev.zeann3th.stresspilot.infrastructure.configs.properties.RequestLogWriterProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseRequestMessagePortTest {

    private DatabaseRequestMessagePort port;

    @AfterEach
    void stopPort() {
        if (port != null) {
            port.stop();
        }
    }

    @Test
    void flushPersistsLargeQueueWithoutDroppingEntries() {
        CapturingRequestLogStore store = new CapturingRequestLogStore(0);
        port = new DatabaseRequestMessagePort(store, transactionTemplate(), properties(250, 10));
        port.start();

        for (int i = 0; i < 5_000; i++) {
            port.write(log(i));
        }
        port.flush();

        assertThat(store.saved).hasSize(5_000);
        assertThat(store.batchSizes).isNotEmpty();
    }

    @Test
    void retriesTransientStoreFailureBeforeDroppingBatch() {
        CapturingRequestLogStore store = new CapturingRequestLogStore(2);
        port = new DatabaseRequestMessagePort(store, transactionTemplate(), properties(50, 10));

        for (int i = 0; i < 10; i++) {
            port.write(log(i));
        }
        port.start();
        port.flush();

        assertThat(store.saved).hasSize(10);
        assertThat(store.attempts.get()).isEqualTo(3);
    }

    @Test
    void backpressurePreventsDropsDuringConcurrentBurstWhenStoreIsTemporarilySlower() {
        int producers = 16;
        int logsPerProducer = 750;
        int expectedLogs = producers * logsPerProducer;
        CapturingRequestLogStore store = new CapturingRequestLogStore(0, 5);
        RequestLogWriterProperties properties = properties(100, 5);
        properties.getDatasource().setQueueCapacity(64);
        properties.getDatasource().setOfferTimeoutMs(1_000);
        port = new DatabaseRequestMessagePort(store, transactionTemplate(), properties);
        port.start();

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(producers)) {
            for (int producer = 0; producer < producers; producer++) {
                int producerIndex = producer;
                pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    int offset = producerIndex * logsPerProducer;
                    for (int i = 0; i < logsPerProducer; i++) {
                        port.write(log(offset + i));
                    }
                });
            }
            start.countDown();
        }

        port.flush();

        assertThat(store.saved).hasSize(expectedLogs);
        assertThat(store.batchSizes).allMatch(size -> size <= 100);
    }

    private static RequestLogWriterProperties properties(int batchSize, long flushIntervalMs) {
        RequestLogWriterProperties properties = new RequestLogWriterProperties();
        properties.getDatasource().setBatchSize(batchSize);
        properties.getDatasource().setFlushIntervalMs(flushIntervalMs);
        properties.getDatasource().setQueueCapacity(200_000);
        properties.getDatasource().setOfferTimeoutMs(50);
        properties.getDatasource().setMaxRetries(3);
        properties.getDatasource().setRetryBaseDelayMs(100);
        return properties;
    }

    private static TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() throws TransactionException {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) throws TransactionException {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) throws TransactionException {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) throws TransactionException {
            }
        });
    }

    private static RequestLogEntity log(int index) {
        return RequestLogEntity.builder()
                .statusCode(200)
                .success(true)
                .responseTime((long) index)
                .request("request-" + index)
                .response("response-" + index)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static final class CapturingRequestLogStore implements RequestLogStore {
        private final int failuresBeforeSuccess;
        private final long saveAllDelayMs;
        private final AtomicInteger attempts = new AtomicInteger();
        private final List<RequestLogEntity> saved = new CopyOnWriteArrayList<>();
        private final List<Integer> batchSizes = new CopyOnWriteArrayList<>();

        private CapturingRequestLogStore(int failuresBeforeSuccess) {
            this(failuresBeforeSuccess, 0);
        }

        private CapturingRequestLogStore(int failuresBeforeSuccess, long saveAllDelayMs) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
            this.saveAllDelayMs = saveAllDelayMs;
        }

        @Override
        public RequestLogEntity save(RequestLogEntity entity) {
            saved.add(entity);
            return entity;
        }

        @Override
        public List<RequestLogEntity> saveAll(Iterable<RequestLogEntity> entities) {
            if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
                throw new IllegalStateException("temporary database failure");
            }
            List<RequestLogEntity> batch = new ArrayList<>();
            entities.forEach(batch::add);
            if (saveAllDelayMs > 0) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(saveAllDelayMs));
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while saving logs");
                }
            }
            batchSizes.add(batch.size());
            saved.addAll(batch);
            return batch;
        }

        @Override
        public RunReport calculateRunReport(String runId, RunEntity run) {
            return RunReport.builder().runId(runId).build();
        }

        @Override
        public void streamLogsByRunId(String runId, Consumer<RequestLogEntity> consumer) {
            saved.forEach(consumer);
        }
    }
}
