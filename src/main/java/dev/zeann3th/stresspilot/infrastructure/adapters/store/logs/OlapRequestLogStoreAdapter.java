package dev.zeann3th.stresspilot.infrastructure.adapters.store.logs;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.ports.store.RequestLogStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.message.datasource", name = "type", havingValue = "secondary")
public class OlapRequestLogStoreAdapter implements RequestLogStore {

    @Qualifier("secondaryJdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    @Override
    public RequestLogEntity save(RequestLogEntity entity) {
        saveAll(List.of(entity));
        return entity;
    }

    @Override
    public List<RequestLogEntity> saveAll(Iterable<RequestLogEntity> entities) {
        List<RequestLogEntity> list = new ArrayList<>();
        entities.forEach(list::add);
        if (list.isEmpty()) return list;

        String sql = """
            INSERT INTO request_logs 
            (id, run_id, endpoint_id, status_code, is_success, response_time, request, response, created_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        jdbcTemplate.batchUpdate(sql, list, 1000, (PreparedStatement ps, RequestLogEntity entity) -> {
            ps.setLong(1, entity.getId() != null ? entity.getId() : 0L);
            ps.setLong(2, entity.getRunId() != null ? entity.getRunId() : 0L);
            ps.setLong(3, entity.getEndpointId() != null ? entity.getEndpointId() : 0L);
            ps.setObject(4, entity.getStatusCode(), java.sql.Types.INTEGER);
            ps.setObject(5, entity.getSuccess() != null ? (entity.getSuccess() ? 1 : 0) : null, java.sql.Types.INTEGER);
            ps.setObject(6, entity.getResponseTime(), java.sql.Types.BIGINT);
            ps.setString(7, entity.getRequest());
            ps.setString(8, entity.getResponse());
            ps.setTimestamp(9, entity.getCreatedAt() != null ? Timestamp.valueOf(entity.getCreatedAt()) : new Timestamp(System.currentTimeMillis()));
        });
        return list;
    }

    @Override
    public void streamLogsByRunId(Long runId, Consumer<RequestLogEntity> consumer) {
        String sql = "SELECT id, endpoint_id, status_code, is_success, response_time, request, response, created_at FROM request_logs WHERE run_id = ?";

        jdbcTemplate.query(sql, rs -> {
            RequestLogEntity log = new RequestLogEntity();
            log.setId(rs.getLong("id"));

            long epId = rs.getLong("endpoint_id");
            if (!rs.wasNull()) {
                EndpointEntity ep = new EndpointEntity();
                ep.setId(epId);
                ep.setName("Endpoint-" + epId);
                log.setEndpoint(ep);
            }

            log.setStatusCode(rs.getInt("status_code"));
            if (rs.wasNull()) log.setStatusCode(null);

            log.setSuccess(rs.getBoolean("is_success"));
            if (rs.wasNull()) log.setSuccess(null);

            log.setResponseTime(rs.getLong("response_time"));
            if (rs.wasNull()) log.setResponseTime(null);

            log.setRequest(rs.getString("request"));
            log.setResponse(rs.getString("response"));

            Timestamp ts = rs.getTimestamp("created_at");
            if (ts != null) log.setCreatedAt(ts.toLocalDateTime());

            consumer.accept(log);
        }, runId);
    }

    @Override
    public RunReport calculateRunReport(Long runId, RunEntity run) {
        String summarySql = """
            SELECT 
              count() as total_requests, 
              countIf(is_success = 1 OR (status_code >= 200 AND status_code < 300)) as success_count, 
              avg(response_time) as avg_response, 
              quantile(0.90)(response_time) as p90, 
              quantile(0.95)(response_time) as p95, 
              min(created_at) as start_time, 
              max(created_at) as end_time 
            FROM request_logs WHERE run_id = ?
        """;

        RunReport.RunReportBuilder builder = RunReport.builder().runId(runId);

        jdbcTemplate.queryForObject(summarySql, (rs, rowNum) -> {
            int totalRequests = rs.getInt("total_requests");
            int successCount = rs.getInt("success_count");
            Timestamp startTs = rs.getTimestamp("start_time");
            Timestamp endTs = rs.getTimestamp("end_time");

            double durationSeconds = 1.0; // Fallback
            if (run != null && run.getStartedAt() != null && run.getCompletedAt() != null) {
                durationSeconds = Duration.between(run.getStartedAt(), run.getCompletedAt()).toMillis() / 1000.0;
            } else if (startTs != null && endTs != null) {
                durationSeconds = Duration.between(startTs.toLocalDateTime(), endTs.toLocalDateTime()).toMillis() / 1000.0;
            }

            builder.totalRequests(totalRequests)
                    .successCount(successCount)
                    .failureCount(totalRequests - successCount)
                    .successRate(totalRequests == 0 ? 0.0 : round(100.0 * successCount / totalRequests))
                    .failureRate(totalRequests == 0 ? 0.0 : round(100.0 * (totalRequests - successCount) / totalRequests))
                    .avgResponse(round(rs.getDouble("avg_response")))
                    .p90(round(rs.getDouble("p90")))
                    .p95(round(rs.getDouble("p95")))
                    .durationSeconds(round(durationSeconds))
                    .tps(durationSeconds <= 0 ? 0.0 : round(totalRequests / durationSeconds));
            return null;
        }, runId);

        String endpointSql = """
            SELECT endpoint_id, count() as requests, avg(response_time) as avg_ms, 
                   quantile(0.90)(response_time) as p90_ms, quantile(0.95)(response_time) as p95_ms 
            FROM request_logs WHERE run_id = ? GROUP BY endpoint_id
        """;

        List<EndpointStats> endpointStats = jdbcTemplate.query(endpointSql, (rs, rowNum) -> {
            Long endpointId = rs.getLong("endpoint_id");
            return EndpointStats.builder()
                    .endpointId(endpointId)
                    .endpointName("Endpoint-" + endpointId)
                    .requests(rs.getInt("requests"))
                    .avgMs(round(rs.getDouble("avg_ms")))
                    .p90Ms(round(rs.getDouble("p90_ms")))
                    .p95Ms(round(rs.getDouble("p95_ms")))
                    .build();
        }, runId);

        builder.endpointStats(endpointStats);

        return builder.build();
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}