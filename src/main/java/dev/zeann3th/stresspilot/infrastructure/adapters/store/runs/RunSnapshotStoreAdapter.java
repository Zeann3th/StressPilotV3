package dev.zeann3th.stresspilot.infrastructure.adapters.store.runs;

import dev.zeann3th.stresspilot.core.domain.entities.RunSnapshotEntity;
import dev.zeann3th.stresspilot.core.ports.store.RunSnapshotStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RunSnapshotStoreAdapter implements RunSnapshotStore {
    private final RunSnapshotJpaRepository runSnapshotJpaRepository;

    @Override
    public RunSnapshotEntity save(RunSnapshotEntity snapshot) {
        return runSnapshotJpaRepository.save(snapshot);
    }

    @Override
    public boolean existsById(String id) {
        return runSnapshotJpaRepository.existsById(id);
    }

    @Override
    public Optional<RunSnapshotEntity> findById(String id) {
        return runSnapshotJpaRepository.findById(id);
    }
}
