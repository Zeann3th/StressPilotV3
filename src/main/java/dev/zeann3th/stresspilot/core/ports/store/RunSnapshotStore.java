package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.RunSnapshotEntity;

import java.util.Optional;

public interface RunSnapshotStore {
    RunSnapshotEntity save(RunSnapshotEntity snapshot);

    boolean existsById(String id);

    Optional<RunSnapshotEntity> findById(String id);
}
