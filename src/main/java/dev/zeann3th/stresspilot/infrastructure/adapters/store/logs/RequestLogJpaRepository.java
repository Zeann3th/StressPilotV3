package dev.zeann3th.stresspilot.infrastructure.adapters.store.logs;

import dev.zeann3th.stresspilot.core.domain.entities.RequestLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequestLogJpaRepository extends JpaRepository<RequestLogEntity, Long> {
    List<RequestLogEntity> findAllByRunId(Long runId);
}
