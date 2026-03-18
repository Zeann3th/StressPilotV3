package dev.zeann3th.stresspilot.infrastructure.adapters.store.projects;

import dev.zeann3th.stresspilot.core.domain.entities.ProjectEntity;
import dev.zeann3th.stresspilot.core.ports.store.ProjectStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ProjectStoreAdapter implements ProjectStore {
    private final ProjectJpaRepository projectJpaRepository;

    @Override
    public ProjectEntity save(ProjectEntity projectEntity) {
        return projectJpaRepository.save(projectEntity);
    }

    @Override
    public Optional<ProjectEntity> findById(Long id) {
        return projectJpaRepository.findById(id);
    }

    @Override
    public Page<ProjectEntity> findAllByCondition(String name, Pageable pageable) {
        String term = null;
        if (name != null && !name.isBlank()) {
            term = "%" + name.toLowerCase().trim() + "%";
        }
        return projectJpaRepository.findAllByCondition(term, pageable);
    }

    @Override
    public void deleteById(Long id) {
        projectJpaRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long projectId) {
        return projectJpaRepository.existsById(projectId);
    }
}
