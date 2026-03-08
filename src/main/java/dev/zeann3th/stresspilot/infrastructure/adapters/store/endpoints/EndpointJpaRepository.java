package dev.zeann3th.stresspilot.infrastructure.adapters.store.endpoints;

import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EndpointJpaRepository extends JpaRepository<EndpointEntity, Long> {
    @Query(
            """
                SELECT e FROM EndpointEntity e
                WHERE (:projectId IS NULL OR e.project.id = :projectId)
                AND (:name IS NULL OR LOWER(e.name) LIKE :name)
            """
    )
    Page<EndpointEntity> findAllByCondition(@Param("projectId") Long projectId, @Param("name") String name, Pageable pageable);

    List<EndpointEntity> findAllByProjectId(Long projectId);
}
