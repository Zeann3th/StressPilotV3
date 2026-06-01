package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentEntity;

import java.util.Optional;
import java.util.List;

public interface EnvironmentStore {
    EnvironmentEntity save(EnvironmentEntity environmentEntity);

    Optional<EnvironmentEntity> findById(Long id);

    List<EnvironmentEntity> findAllByProjectId(Long projectId);

    void deleteById(Long id);
}
