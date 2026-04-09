package dev.zeann3th.stresspilot.infrastructure.adapters.store.functions;

import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import dev.zeann3th.stresspilot.core.ports.store.FunctionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FunctionStoreAdapter implements FunctionStore {

    private final FunctionJpaRepository functionJpaRepository;

    @Override
    public FunctionEntity save(FunctionEntity functionEntity) {
        return functionJpaRepository.save(functionEntity);
    }

    @Override
    public List<FunctionEntity> findAll() {
        return functionJpaRepository.findAll();
    }

    @Override
    public Optional<FunctionEntity> findById(Long id) {
        return functionJpaRepository.findById(id);
    }

    @Override
    public Page<FunctionEntity> findAll(String name, Pageable pageable) {
        return functionJpaRepository.findAllByName(name, pageable);
    }

    @Override
    public void deleteById(Long id) {
        functionJpaRepository.deleteById(id);
    }
}
