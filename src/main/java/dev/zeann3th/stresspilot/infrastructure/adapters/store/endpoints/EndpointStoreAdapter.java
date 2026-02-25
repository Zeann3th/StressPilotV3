package dev.zeann3th.stresspilot.infrastructure.adapters.store.endpoints;

import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import dev.zeann3th.stresspilot.core.ports.store.EndpointStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EndpointStoreAdapter implements EndpointStore {
    private final EndpointJpaRepository endpointJpaRepository;

    @Override
    public EndpointEntity save(EndpointEntity endpointEntity) {
        return endpointJpaRepository.save(endpointEntity);
    }

    @Override
    public List<EndpointEntity> saveAll(Iterable<EndpointEntity> entities) {
        return endpointJpaRepository.saveAll(entities);
    }

    @Override
    public Optional<EndpointEntity> findById(Long id) {
        return endpointJpaRepository.findById(id);
    }

    @Override
    public Page<EndpointEntity> findAllByCondition(Long projectId, String name, Pageable pageable) {
        return endpointJpaRepository.findAllByCondition(projectId, name, pageable);
    }

    @Override
    public List<EndpointEntity> findAllByProjectId(Long projectId) {
        return endpointJpaRepository.findAllByProjectId(projectId);
    }

    @Override
    public void deleteById(Long id) {
        endpointJpaRepository.deleteById(id);
    }

    @Override
    public void deleteAllByProjectId(Long projectId) {
        endpointJpaRepository.deleteAllByProjectId(projectId);
    }
}
