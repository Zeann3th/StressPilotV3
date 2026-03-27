package dev.zeann3th.stresspilot.infrastructure.adapters.store.logs;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Stream;

@Repository
public interface OltpRequestLogRepository extends JpaRepository<RequestLogEntity, Long> {

    @QueryHints(@QueryHint(name = HibernateHints.HINT_FETCH_SIZE, value = "1000"))
    @Query("SELECT l FROM RequestLogEntity l LEFT JOIN FETCH l.endpoint WHERE l.run.id = :runId")
    Stream<RequestLogEntity> streamAllByRunId(@Param("runId") String runId);

    @Query("SELECT l.responseTime FROM RequestLogEntity l WHERE l.run.id = :runId AND l.responseTime IS NOT NULL")
    List<Long> findResponseTimesByRunId(@Param("runId") String runId);

    @Query("SELECT l.endpoint.id, l.responseTime FROM RequestLogEntity l WHERE l.run.id = :runId AND l.responseTime IS NOT NULL")
    List<Object[]> findEndpointResponseTimesByRunId(@Param("runId") String runId);

    @Query("""
        SELECT
            COUNT(l.id),
            SUM(CASE WHEN l.success = true OR (l.statusCode >= 200 AND l.statusCode < 300) THEN 1L ELSE 0L END),
            AVG(CAST(l.responseTime AS double)),
            MIN(l.createdAt),
            MAX(l.createdAt)
        FROM RequestLogEntity l
        WHERE l.run.id = :runId
    """)
    List<Object[]> getRunSummaryStats(@Param("runId") String runId);

    @Query("""
        SELECT
            e.id,
            e.name,
            COUNT(l.id),
            AVG(CAST(l.responseTime AS double))
        FROM RequestLogEntity l
        LEFT JOIN l.endpoint e
        WHERE l.run.id = :runId
        GROUP BY e.id, e.name
    """)
    List<Object[]> getEndpointSummaryStats(@Param("runId") String runId);
}