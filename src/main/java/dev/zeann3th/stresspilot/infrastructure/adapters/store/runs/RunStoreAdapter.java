package dev.zeann3th.stresspilot.infrastructure.adapters.store.runs;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RunStoreAdapter implements RunStore {
    private final RunJpaRepository runJpaRepository;

    @Override
    public RunEntity save(RunEntity runEntity) {
        return runJpaRepository.save(runEntity);
    }

    @Override
    public Optional<RunEntity> findById(Long id) {
        return runJpaRepository.findById(id);
    }

    @Override
    public List<RunEntity> findAllByFlowId(Long flowId) {
        return runJpaRepository.findByFlowIdOrderByStartedAtDesc(flowId);
    }

    @Override
    public Optional<RunEntity> findLastRunByFlowId(Long flowId) {
        return runJpaRepository.findTopByFlowIdOrderByStartedAtDesc(flowId);
    }

    @Override
    public List<RunEntity> findAll() {
        return runJpaRepository.findAllOrderByStartedAtDesc();
    }

    @Override
    public void deleteById(Long id) {
        runJpaRepository.deleteById(id);
    }
}
