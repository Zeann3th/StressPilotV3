package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;

import java.util.List;
import java.util.Optional;

public interface FlowStepStore {
    FlowStepEntity save(FlowStepEntity flowStepEntity);

    List<FlowStepEntity> saveAll(Iterable<FlowStepEntity> entities);

    Optional<FlowStepEntity> findById(String id);

    List<FlowStepEntity> findAllByFlowId(Long flowId);

    void deleteById(String id);

    void deleteAllByFlowId(Long flowId);
}
