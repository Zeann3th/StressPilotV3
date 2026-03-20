package dev.zeann3th.stresspilot.infrastructure.adapters.store.flows;

import dev.zeann3th.stresspilot.core.domain.entities.FlowStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlowStepJpaRepository extends JpaRepository<FlowStepEntity, String> {
    @Query("SELECT s FROM FlowStepEntity s WHERE s.flow.id = :flowId")
    List<FlowStepEntity> findAllByFlowId(@Param("flowId") Long flowId);

    @Query("SELECT s FROM FlowStepEntity s LEFT JOIN FETCH s.endpoint WHERE s.flow.id = :flowId")
    List<FlowStepEntity> findAllByFlowIdWithEndpoint(@Param("flowId") Long flowId);

    @Modifying
    @Query("DELETE FROM FlowStepEntity s WHERE s.flow.id = :flowId")
    void deleteAllByFlowId(@Param("flowId") Long flowId);
}
