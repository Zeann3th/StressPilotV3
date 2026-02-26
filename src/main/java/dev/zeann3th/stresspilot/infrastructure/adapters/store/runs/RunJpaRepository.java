package dev.zeann3th.stresspilot.infrastructure.adapters.store.runs;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RunJpaRepository extends JpaRepository<RunEntity, Long> {
    List<RunEntity> findByFlowIdOrderByStartedAtDesc(Long flowId);

    Optional<RunEntity> findTopByFlowIdOrderByStartedAtDesc(Long flowId);

    @Query("SELECT r FROM RunEntity r ORDER BY r.startedAt DESC")
    List<RunEntity> findAllOrderByStartedAtDesc();
}
