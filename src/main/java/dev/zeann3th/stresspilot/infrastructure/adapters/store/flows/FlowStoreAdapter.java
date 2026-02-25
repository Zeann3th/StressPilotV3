package dev.zeann3th.stresspilot.infrastructure.adapters.store.flows;

import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;
import dev.zeann3th.stresspilot.core.ports.store.FlowStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FlowStoreAdapter implements FlowStore {
    private final FlowJpaRepository flowJpaRepository;

    @Override
    public FlowEntity save(FlowEntity flowEntity) {
        return flowJpaRepository.save(flowEntity);
    }

    @Override
    public Optional<FlowEntity> findById(Long id) {
        return flowJpaRepository.findById(id);
    }

    @Override
    public org.springframework.data.domain.Page<FlowEntity> findAllByCondition(Long projectId, String name, org.springframework.data.domain.Pageable pageable) {
        return flowJpaRepository.findAllByCondition(projectId, name, pageable);
    }

    @Override
    public List<FlowEntity> findAllByProjectId(Long projectId) {
        return flowJpaRepository.findAllByProjectId(projectId);
    }

    @Override
    public void deleteById(Long id) {
        flowJpaRepository.deleteById(id);
    }

    @Override
    public void deleteAllByProjectId(Long projectId) {
        flowJpaRepository.deleteAllByProjectId(projectId);
    }
}
