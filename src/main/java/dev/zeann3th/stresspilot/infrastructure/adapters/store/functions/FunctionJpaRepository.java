package dev.zeann3th.stresspilot.infrastructure.adapters.store.functions;

import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FunctionJpaRepository extends JpaRepository<FunctionEntity, Long> {
    @Query("SELECT f FROM FunctionEntity f WHERE (:name IS NULL OR f.name LIKE %:name%)")
    Page<FunctionEntity> findAllByName(@Param("name") String name, Pageable pageable);
}
