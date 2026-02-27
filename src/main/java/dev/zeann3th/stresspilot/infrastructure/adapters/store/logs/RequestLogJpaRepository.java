package dev.zeann3th.stresspilot.infrastructure.adapters.store.logs;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestLogJpaRepository extends JpaRepository<RequestLogEntity, Long> {

    @Query("SELECT l FROM RequestLogEntity l JOIN FETCH l.endpoint WHERE l.run.id = :runId")
    List<RequestLogEntity> findAllByRunId(@Param("runId") Long runId);
}
