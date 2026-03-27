package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RunStore {
    RunEntity save(RunEntity runEntity);

    Optional<RunEntity> findById(String id);

    List<RunEntity> findAllByFlowId(Long flowId);

    Optional<RunEntity> findLastRunByFlowId(Long flowId);

    List<RunEntity> findAll();

    void deleteById(String id);

    int finalizeRun(String id, String status, LocalDateTime completedAt);

    boolean existsById(String runId);
}