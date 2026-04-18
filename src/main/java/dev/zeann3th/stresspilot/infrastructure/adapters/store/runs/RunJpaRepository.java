package dev.zeann3th.stresspilot.infrastructure.adapters.store.runs;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RunJpaRepository extends JpaRepository<RunEntity, String> {
    @Query("SELECT r FROM RunEntity r WHERE r.flow.id = :flowId ORDER BY r.startedAt DESC")
    List<RunEntity> findByFlowIdOrderByStartedAtDesc(@Param("flowId") Long flowId);

    @Query("SELECT r FROM RunEntity r WHERE r.flow.id = :flowId ORDER BY r.startedAt DESC LIMIT 1")
    Optional<RunEntity> findTopByFlowIdOrderByStartedAtDesc(@Param("flowId") Long flowId);

    @Query("SELECT r FROM RunEntity r ORDER BY r.startedAt DESC")
    List<RunEntity> findAllOrderByStartedAtDesc();

    @Modifying
    @Query("UPDATE RunEntity r SET r.status = :status, r.completedAt = :completedAt " +
            "WHERE r.id = :id AND r.status = 'RUNNING'")
    int finalizeRun(@Param("id") String id,
                    @Param("status") String status,
                    @Param("completedAt") LocalDateTime completedAt);
}
