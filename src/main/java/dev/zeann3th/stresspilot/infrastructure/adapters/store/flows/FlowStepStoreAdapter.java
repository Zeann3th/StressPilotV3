package dev.zeann3th.stresspilot.infrastructure.adapters.store.flows;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import dev.zeann3th.stresspilot.core.ports.store.FlowStepStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class FlowStepStoreAdapter implements FlowStepStore {
    private final FlowStepJpaRepository flowStepJpaRepository;

    @Override
    public FlowStepEntity save(FlowStepEntity flowStepEntity) {
        return flowStepJpaRepository.save(flowStepEntity);
    }

    @Override
    public List<FlowStepEntity> saveAll(Iterable<FlowStepEntity> entities) {
        return flowStepJpaRepository.saveAll(entities);
    }

    @Override
    public Optional<FlowStepEntity> findById(String id) {
        return flowStepJpaRepository.findById(id);
    }

    @Override
    public List<FlowStepEntity> findAllByFlowId(Long flowId) {
        return flowStepJpaRepository.findAllByFlowId(flowId);
    }

    @Override
    public List<FlowStepEntity> findAllByFlowIdWithEndpoint(Long flowId) {
        return flowStepJpaRepository.findAllByFlowIdWithEndpoint(flowId);
    }

    @Override
    public void deleteById(String id) {
        flowStepJpaRepository.deleteById(id);
    }

    @Override
    public void deleteAllByFlowId(Long flowId) {
        flowStepJpaRepository.deleteAllByFlowId(flowId);
    }
}
