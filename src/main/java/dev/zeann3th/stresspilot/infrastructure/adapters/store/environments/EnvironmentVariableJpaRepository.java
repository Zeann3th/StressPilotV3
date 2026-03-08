package dev.zeann3th.stresspilot.infrastructure.adapters.store.environments;

import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnvironmentVariableJpaRepository extends JpaRepository<EnvironmentVariableEntity, Long> {
    List<EnvironmentVariableEntity> findAllByEnvironmentId(Long environmentId);

    List<EnvironmentVariableEntity> findAllByEnvironmentIdAndActiveTrue(Long environmentId);

    void deleteAllByEnvironmentId(Long environmentId);
}
