package dev.zeann3th.stresspilot.infrastructure.adapters.store.flows;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowStepJpaRepository extends JpaRepository<FlowStepEntity, String> {
    List<FlowStepEntity> findAllByFlowId(Long flowId);

    @Query("SELECT s FROM FlowStepEntity s LEFT JOIN FETCH s.endpoint WHERE s.flowId = :flowId")
    List<FlowStepEntity> findAllByFlowIdWithEndpoint(Long flowId);

    void deleteAllByFlowId(Long flowId);
}
