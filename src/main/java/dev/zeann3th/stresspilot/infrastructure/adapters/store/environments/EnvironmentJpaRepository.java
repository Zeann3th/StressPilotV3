package dev.zeann3th.stresspilot.infrastructure.adapters.store.environments;

import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnvironmentJpaRepository extends JpaRepository<EnvironmentEntity, Long> {
}
