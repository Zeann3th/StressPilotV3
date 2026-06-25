package dev.zeann3th.stresspilot.infrastructure.adapters.store.logs;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OltpRequestLogStoreAdapterTest {

    @Autowired
    private OltpRequestLogStoreAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM request_logs");
        jdbcTemplate.execute("DELETE FROM runs");
        jdbcTemplate.execute("DELETE FROM flows");
        jdbcTemplate.execute("DELETE FROM endpoints");
        jdbcTemplate.execute("DELETE FROM projects");
        jdbcTemplate.execute("DELETE FROM environments");

        jdbcTemplate.execute("INSERT INTO environments (id, name) VALUES (999, 'Default')");
        jdbcTemplate.execute("INSERT INTO projects (id, name, description, environment_id) VALUES (999, 'Test Project', 'Test Desc', 999)");
        jdbcTemplate.execute("INSERT INTO endpoints (id, name, type, project_id) VALUES (999, 'Test Endpoint', 'HTTP', 999)");
        jdbcTemplate.execute("INSERT INTO flows (id, project_id, name, type, description) VALUES (999, 999, 'Test Flow', 'DEFAULT', 'Desc')");
        jdbcTemplate.execute("INSERT INTO runs (id, flow_id, status, threads, ramp_up_duration, started_at) VALUES ('test-run-123', 999, 'COMPLETED', 2, 0, '2026-06-25 12:00:00')");
    }

    @Test
    @Transactional
    void streamLogsByRunIdFetchesAllLogsInSliceBatches() {
        // Insert 200,000 test request logs using batch update for high performance
        List<Object[]> batchArgs = new ArrayList<>();
        for (int i = 1; i <= 200000; i++) {
            batchArgs.add(new Object[]{
                100000 + i, // id
                "test-run-123", // run_id
                999L, // endpoint_id
                i % 2 == 0 ? 200 : 500, // status_code
                i % 2 == 0 ? 1 : 0, // is_success
                100L + (i % 50), // response_time
                "req_" + i, // request
                "res_" + i, // response
                "2026-06-25 12:00:00" // created_at
            });
        }
        jdbcTemplate.batchUpdate(
            "INSERT INTO request_logs (id, run_id, endpoint_id, status_code, is_success, response_time, request, response, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            batchArgs
        );

        // Set the page size to 2000 to force exactly 100 slice page queries under the hood
        adapter.setPageSize(2000);

        List<RequestLogEntity> results = new ArrayList<>();
        adapter.streamLogsByRunId("test-run-123", results::add);

        // Verify that all 200,000 logs are fetched in order of id
        assertThat(results).hasSize(200000);
        assertThat(results.get(0).getId()).isEqualTo(100001L);
        assertThat(results.get(199999).getId()).isEqualTo(300000L);

        assertThat(results.get(0).getResponseTime()).isEqualTo(101L);
        assertThat(results.get(199999).getResponseTime()).isEqualTo(100L);
    }
}
