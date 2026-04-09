package dev.zeann3th.stresspilot.infrastructure.adapters.store.functions;

import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FunctionJpaRepository extends JpaRepository<FunctionEntity, Long> {
}
