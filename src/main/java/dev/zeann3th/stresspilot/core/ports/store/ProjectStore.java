package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ProjectStore {
    ProjectEntity save(ProjectEntity projectEntity);

    Optional<ProjectEntity> findById(Long id);

    Page<ProjectEntity> findAllByCondition(String name, Pageable pageable);

    void deleteById(Long id);

    boolean existsById(Long projectId);
}
