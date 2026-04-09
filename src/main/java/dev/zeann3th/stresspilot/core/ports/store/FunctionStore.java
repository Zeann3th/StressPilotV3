package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface FunctionStore {
    FunctionEntity save(FunctionEntity functionEntity);

    List<FunctionEntity> findAll();

    Optional<FunctionEntity> findById(Long id);

    Page<FunctionEntity> findAll(String name, Pageable pageable);

    void deleteById(Long id);
}
