package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.FlowEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface FlowStore {
    FlowEntity save(FlowEntity flowEntity);

    Optional<FlowEntity> findById(Long id);

    Page<FlowEntity> findAllByCondition(Long projectId, String name, Pageable pageable);

    List<FlowEntity> findAllByProjectId(Long projectId);

    void deleteById(Long id);

    void deleteAllByProjectId(Long projectId);
}
