package dev.zeann3th.stresspilot.infrastructure.adapters.store.environments;

import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentVariableStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EnvironmentVariableStoreAdapter implements EnvironmentVariableStore {
    private final EnvironmentVariableJpaRepository repository;

    @Override
    public EnvironmentVariableEntity save(EnvironmentVariableEntity entity) {
        return repository.save(entity);
    }

    @Override
    public List<EnvironmentVariableEntity> saveAll(Iterable<EnvironmentVariableEntity> entities) {
        return repository.saveAll(entities);
    }

    @Override
    public Optional<EnvironmentVariableEntity> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<EnvironmentVariableEntity> findAllByEnvironmentId(Long environmentId) {
        return repository.findAllByEnvironmentId(environmentId);
    }

    @Override
    public List<EnvironmentVariableEntity> findAllByEnvironmentIdAndActiveTrue(Long environmentId) {
        return repository.findAllByEnvironmentIdAndActiveTrue(environmentId);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        repository.deleteAllById(ids);
    }

    @Override
    public void deleteAllByEnvironmentId(Long environmentId) {
        repository.deleteAllByEnvironmentId(environmentId);
    }
}
