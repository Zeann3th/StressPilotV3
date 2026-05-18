package dev.zeann3th.stresspilot.infrastructure.adapters.store.runs;

import dev.zeann3th.stresspilot.core.domain.entities.RunSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RunSnapshotJpaRepository extends JpaRepository<RunSnapshotEntity, String> {
}
