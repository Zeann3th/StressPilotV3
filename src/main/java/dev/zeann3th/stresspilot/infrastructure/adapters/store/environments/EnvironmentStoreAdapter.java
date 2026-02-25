package dev.zeann3th.stresspilot.infrastructure.adapters.store.environments;

import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentEntity;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EnvironmentStoreAdapter implements EnvironmentStore {
    private final EnvironmentJpaRepository environmentJpaRepository;

    @Override
    public EnvironmentEntity save(EnvironmentEntity environmentEntity) {
        return environmentJpaRepository.save(environmentEntity);
    }

    @Override
    public Optional<EnvironmentEntity> findById(Long id) {
        return environmentJpaRepository.findById(id);
    }

    @Override
    public void deleteById(Long id) {
        environmentJpaRepository.deleteById(id);
    }
}
