package dev.zeann3th.stresspilot.infrastructure.adapters.store.environments;

import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnvironmentVariableJpaRepository extends JpaRepository<EnvironmentVariableEntity, Long> {
    @Query("SELECT v FROM EnvironmentVariableEntity v WHERE v.environment.id = :environmentId")
    List<EnvironmentVariableEntity> findAllByEnvironmentId(@Param("environmentId") Long environmentId);

    @Query("SELECT v FROM EnvironmentVariableEntity v WHERE v.environment.id = :environmentId AND v.active = true")
    List<EnvironmentVariableEntity> findAllByEnvironmentIdAndActiveTrue(@Param("environmentId") Long environmentId);

    @Modifying
    @Query("DELETE FROM EnvironmentVariableEntity v WHERE v.environment.id = :environmentId")
    void deleteAllByEnvironmentId(@Param("environmentId") Long environmentId);
}
