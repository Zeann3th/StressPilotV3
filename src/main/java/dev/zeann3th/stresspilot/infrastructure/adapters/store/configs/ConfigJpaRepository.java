package dev.zeann3th.stresspilot.infrastructure.adapters.store.configs;

import dev.zeann3th.stresspilot.core.domain.entities.ConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigJpaRepository extends JpaRepository<ConfigEntity, Long> {
    Optional<ConfigEntity> findByKey(String key);

    List<ConfigEntity> findAllByKeyIn(Iterable<String> keys);
}
