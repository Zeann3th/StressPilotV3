package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;

import java.util.List;
import java.util.Optional;

public interface RunStore {
    RunEntity save(RunEntity runEntity);

    Optional<RunEntity> findById(Long id);

    List<RunEntity> findAllByFlowId(Long flowId);

    List<RunEntity> findAll();

    void deleteById(Long id);
}
