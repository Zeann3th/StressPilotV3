package dev.zeann3th.stresspilot.core.services.runs;

import dev.zeann3th.stresspilot.core.domain.commands.run.RunAnalysisDump;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.RunStatus;
import dev.zeann3th.stresspilot.core.ports.store.RequestLogStore;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RunServiceImplTest {

    @Test
    void getRunAnalysisDumpReturnsReportAndEveryStreamedLog() {
        RunEntity run = RunEntity.builder()
                .id("run-1")
                .status(RunStatus.COMPLETED.name())
                .threads(3)
                .duration(60)
                .rampUpDuration(5)
                .startedAt(LocalDateTime.parse("2026-06-05T10:00:00"))
                .completedAt(LocalDateTime.parse("2026-06-05T10:01:00"))
                .build();
        RunReport report = RunReport.builder()
                .runId("run-1")
                .totalRequests(2)
                .successCount(1)
                .failureCount(1)
                .build();
        EndpointEntity login = EndpointEntity.builder().id(10L).name("Login").type("HTTP").build();
        EndpointEntity checkout = EndpointEntity.builder().id(11L).name("Checkout").type("HTTP").build();
        List<RequestLogEntity> logs = List.of(
                RequestLogEntity.builder()
                        .id(1L)
                        .endpoint(login)
                        .statusCode(200)
                        .success(true)
                        .responseTime(120L)
                        .correlationId("corr-1")
                        .request("{\"variables_snapshot\":{\"__stresspilot_active_threads\":2}}")
                        .response("{\"ok\":true}")
                        .createdAt(LocalDateTime.parse("2026-06-05T10:00:01"))
                        .build(),
                RequestLogEntity.builder()
                        .id(2L)
                        .endpoint(checkout)
                        .statusCode(500)
                        .success(false)
                        .responseTime(900L)
                        .correlationId("corr-2")
                        .request("__stresspilot_active_threads=3")
                        .response("{\"error\":\"boom\"}")
                        .createdAt(LocalDateTime.parse("2026-06-05T10:00:02"))
                        .build());
        FakeRunStore runStore = new FakeRunStore(run);
        FakeRequestLogStore requestLogStore = new FakeRequestLogStore(report, logs);
        RunServiceImpl service = new RunServiceImpl(runStore, requestLogStore, event -> {
        });

        RunAnalysisDump dump = service.getRunAnalysisDump("run-1");

        assertThat(dump.getRun())
                .extracting("id", "status", "threads", "duration", "rampUpDuration", "startedAt", "completedAt")
                .containsExactly("run-1", RunStatus.COMPLETED.name(), 3, 60, 5,
                        LocalDateTime.parse("2026-06-05T10:00:00"),
                        LocalDateTime.parse("2026-06-05T10:01:00"));
        assertThat(dump.getReport()).isSameAs(report);
        assertThat(dump.getLogCount()).isEqualTo(2);
        assertThat(dump.getLogs()).hasSize(2);
        assertThat(dump.getLogs().getFirst())
                .extracting("id", "endpointId", "endpointName", "statusCode", "success", "responseTime", "correlationId", "activeThreads", "request", "response")
                .containsExactly(1L, 10L, "Login", 200, true, 120L, "corr-1", 2,
                        "{\"variables_snapshot\":{\"__stresspilot_active_threads\":2}}", "{\"ok\":true}");
        assertThat(dump.getLogs().get(1))
                .extracting("id", "endpointId", "endpointName", "statusCode", "success", "responseTime", "correlationId", "activeThreads", "request", "response")
                .containsExactly(2L, 11L, "Checkout", 500, false, 900L, "corr-2", 3,
                        "__stresspilot_active_threads=3", "{\"error\":\"boom\"}");
        assertThat(requestLogStore.streamedRunId).isEqualTo("run-1");
    }

    @Test
    void extractsActiveThreadsFromStructuredVariablesSnapshot() throws Exception {
        assertThat(extract("""
                {"variables_snapshot":{"token":"abc","__stresspilot_active_threads":7}}
                """)).isEqualTo(7);
    }

    @Test
    void extractsActiveThreadsFromLegacyMarker() throws Exception {
        assertThat(extract("__stresspilot_active_threads=4")).isEqualTo(4);
    }

    private Integer extract(String request) throws Exception {
        RunServiceImpl service = new RunServiceImpl(null, null, null);
        Method method = RunServiceImpl.class.getDeclaredMethod("extractActiveThreads", String.class);
        method.setAccessible(true);
        return (Integer) method.invoke(service, request);
    }

    private record FakeRunStore(RunEntity run) implements RunStore {
        @Override
        public RunEntity save(RunEntity runEntity) {
            return runEntity;
        }

        @Override
        public Optional<RunEntity> findById(String id) {
            return run.getId().equals(id) ? Optional.of(run) : Optional.empty();
        }

        @Override
        public List<RunEntity> findAllByFlowId(Long flowId) {
            return List.of(run);
        }

        @Override
        public Optional<RunEntity> findLastRunByFlowId(Long flowId) {
            return Optional.of(run);
        }

        @Override
        public List<RunEntity> findAll() {
            return List.of(run);
        }

        @Override
        public void deleteById(String id) {
        }

        @Override
        public int finalizeRun(String id, String status, LocalDateTime completedAt) {
            return 1;
        }
    }

    private static final class FakeRequestLogStore implements RequestLogStore {
        private final RunReport report;
        private final List<RequestLogEntity> logs;
        private String streamedRunId;

        private FakeRequestLogStore(RunReport report, List<RequestLogEntity> logs) {
            this.report = report;
            this.logs = logs;
        }

        @Override
        public RequestLogEntity save(RequestLogEntity entity) {
            return entity;
        }

        @Override
        public List<RequestLogEntity> saveAll(Iterable<RequestLogEntity> entities) {
            return logs;
        }

        @Override
        public RunReport calculateRunReport(String runId, RunEntity run) {
            return report;
        }

        @Override
        public void streamLogsByRunId(String runId, Consumer<RequestLogEntity> consumer) {
            streamedRunId = runId;
            logs.forEach(consumer);
        }
    }
}
