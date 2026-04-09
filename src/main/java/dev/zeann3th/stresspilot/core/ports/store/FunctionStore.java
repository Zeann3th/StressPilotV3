package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;

import java.util.List;

public interface FunctionStore {
    FunctionEntity save(FunctionEntity functionEntity);

    List<FunctionEntity> findAll();

    void deleteById(Long id);
}
