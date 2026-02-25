package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;

import java.util.List;
import java.util.Optional;

public interface EnvironmentVariableStore {
    EnvironmentVariableEntity save(EnvironmentVariableEntity entity);

    List<EnvironmentVariableEntity> saveAll(Iterable<EnvironmentVariableEntity> entities);

    Optional<EnvironmentVariableEntity> findById(Long id);

    List<EnvironmentVariableEntity> findAllByEnvironmentId(Long environmentId);

    List<EnvironmentVariableEntity> findAllByEnvironmentIdAndActiveTrue(Long environmentId);

    void deleteById(Long id);

    void deleteAllById(Iterable<? extends Long> ids);

    void deleteAllByEnvironmentId(Long environmentId);
}
