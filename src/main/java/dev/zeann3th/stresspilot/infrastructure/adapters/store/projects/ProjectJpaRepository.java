package dev.zeann3th.stresspilot.infrastructure.adapters.store.projects;

import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, Long> {
    @Query("SELECT p FROM ProjectEntity p WHERE (:name IS NULL OR LOWER(p.name) LIKE :name)")
    Page<ProjectEntity> findAllByCondition(@Param("name") String name, Pageable pageable);
}
