package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RunStore {
    RunEntity save(RunEntity runEntity);

    Optional<RunEntity> findById(Long id);

    List<RunEntity> findAllByFlowId(Long flowId);

    Optional<RunEntity> findLastRunByFlowId(Long flowId);

    List<RunEntity> findAll();

    void deleteById(Long id);

    int finalizeRun(Long id, String status, LocalDateTime completedAt);

    boolean existsById(Long runId);
}
