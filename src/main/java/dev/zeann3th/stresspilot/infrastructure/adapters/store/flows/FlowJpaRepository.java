package dev.zeann3th.stresspilot.infrastructure.adapters.store.flows;

import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowJpaRepository extends JpaRepository<FlowEntity, Long> {
    @Query(
            """
                SELECT f FROM FlowEntity f
                WHERE (:projectId IS NULL OR f.project.id = :projectId)
                AND (:name IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :name, '%')))
            """
    )
    Page<FlowEntity> findAllByCondition(@Param("projectId") Long projectId, @Param("name") String name, Pageable pageable);

    List<FlowEntity> findAllByProjectId(Long projectId);

    void deleteAllByProjectId(Long projectId);
}
