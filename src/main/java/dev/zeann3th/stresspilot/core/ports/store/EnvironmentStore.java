package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentEntity;

import java.util.Optional;

public interface EnvironmentStore {
    EnvironmentEntity save(EnvironmentEntity environmentEntity);

    Optional<EnvironmentEntity> findById(Long id);

    void deleteById(Long id);
}
