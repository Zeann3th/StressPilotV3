package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.RunSnapshotEntity;

public interface RunSnapshotStore {
    RunSnapshotEntity save(RunSnapshotEntity snapshot);

    boolean existsById(String id);
}
