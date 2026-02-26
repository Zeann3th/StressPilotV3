package dev.zeann3th.stresspilot.core.services.environments.impl;

import dev.zeann3th.stresspilot.core.domain.commands.environment.UpdateEnvironmentVariablesCommand;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentEntity;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentStore;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentVariableStore;
import dev.zeann3th.stresspilot.core.services.environments.EnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnvironmentServiceImpl implements EnvironmentService {

    private final EnvironmentVariableStore envVarStore;
    private final EnvironmentStore envStore;

    @Override
    public List<EnvironmentVariableEntity> getEnvironmentVariables(Long environmentId) {
        return envVarStore.findAllByEnvironmentId(environmentId);
    }

    @Override
    @Transactional
    public void updateEnvironmentVariables(Long environmentId, UpdateEnvironmentVariablesCommand cmd) {
        EnvironmentEntity env = envStore.findById(environmentId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.SP0001,
                        Map.of(Constants.REASON, "Environment " + environmentId + " not found")));

        List<EnvironmentVariableEntity> current = envVarStore.findAllByEnvironmentId(environmentId);
        Map<Long, EnvironmentVariableEntity> byId = current.stream()
                .collect(Collectors.toMap(EnvironmentVariableEntity::getId, Function.identity()));
        Set<String> currentKeys = current.stream()
                .map(EnvironmentVariableEntity::getKey)
                .collect(Collectors.toCollection(HashSet::new));

        handleRemove(environmentId, currentKeys, byId, cmd);
        handleUpdate(currentKeys, byId, cmd);
        handleAdd(env, currentKeys, cmd);
    }

    private void handleRemove(Long environmentId, Set<String> currentKeys,
                               Map<Long, EnvironmentVariableEntity> byId,
                               UpdateEnvironmentVariablesCommand cmd) {
        if (cmd.getRemoved() == null || cmd.getRemoved().isEmpty()) return;
        for (Long id : cmd.getRemoved()) {
            if (!byId.containsKey(id))
                throw CommandExceptionBuilder.exception(ErrorCode.SP0001,
                        Map.of(Constants.REASON, "Variable id " + id + " not in environment " + environmentId));
        }
        for (Long id : cmd.getRemoved()) {
            EnvironmentVariableEntity v = byId.remove(id);
            if (v != null) currentKeys.remove(v.getKey());
        }
        envVarStore.deleteAllById(cmd.getRemoved());
    }

    private void handleUpdate(Set<String> currentKeys,
                               Map<Long, EnvironmentVariableEntity> byId,
                               UpdateEnvironmentVariablesCommand cmd) {
        if (cmd.getUpdated() == null || cmd.getUpdated().isEmpty()) return;
        List<EnvironmentVariableEntity> toSave = new ArrayList<>();
        for (UpdateEnvironmentVariablesCommand.Update upd : cmd.getUpdated()) {
            EnvironmentVariableEntity entity = byId.get(upd.getId());
            if (entity == null)
                throw CommandExceptionBuilder.exception(ErrorCode.SP0001,
                        Map.of(Constants.REASON, "Variable id " + upd.getId() + " not found"));
            String oldKey = entity.getKey();
            String newKey = upd.getKey();
            if (!Objects.equals(oldKey, newKey) && currentKeys.contains(newKey))
                throw CommandExceptionBuilder.exception(ErrorCode.SP0001,
                        Map.of(Constants.REASON, "Duplicate key: " + newKey));
            entity.setKey(newKey);
            entity.setValue(upd.getValue());
            entity.setActive(upd.isActive());
            currentKeys.remove(oldKey);
            currentKeys.add(newKey);
            toSave.add(entity);
        }
        envVarStore.saveAll(toSave);
    }

    private void handleAdd(EnvironmentEntity env, Set<String> currentKeys,
                            UpdateEnvironmentVariablesCommand cmd) {
        if (cmd.getAdded() == null || cmd.getAdded().isEmpty()) return;
        List<EnvironmentVariableEntity> toSave = new ArrayList<>();
        for (UpdateEnvironmentVariablesCommand.Add add : cmd.getAdded()) {
            if (currentKeys.contains(add.getKey()))
                throw CommandExceptionBuilder.exception(ErrorCode.SP0001,
                        Map.of(Constants.REASON, "Duplicate key: " + add.getKey()));
            toSave.add(EnvironmentVariableEntity.builder()
                    .environment(env)
                    .key(add.getKey())
                    .value(add.getValue())
                    .active(true)
                    .build());
            currentKeys.add(add.getKey());
        }
        envVarStore.saveAll(toSave);
    }
}
