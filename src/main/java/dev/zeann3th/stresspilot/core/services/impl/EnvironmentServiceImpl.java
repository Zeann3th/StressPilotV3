package dev.zeann3th.stresspilot.core.services.impl;

import dev.zeann3th.stresspilot.core.domain.commands.environment.UpdateEnvironmentVariablesCommand;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentEntity;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.BusinessExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentStore;
import dev.zeann3th.stresspilot.core.ports.store.EnvironmentVariableStore;
import dev.zeann3th.stresspilot.core.services.EnvironmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnvironmentServiceImpl implements EnvironmentService {

    private final EnvironmentStore environmentStore;
    private final EnvironmentVariableStore envVarStore;

    @Override
    public List<EnvironmentVariableEntity> getEnvironmentVariables(Long environmentId) {
        return envVarStore.findAllByEnvironmentId(environmentId);
    }

    @Override
    @Transactional
    public void updateEnvironmentVariables(UpdateEnvironmentVariablesCommand command) {
        Long environmentId = command.getEnvironmentId();
        EnvironmentEntity environment = environmentStore.findById(environmentId)
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                        Map.of(Constants.REASON, "Environment not found: " + environmentId)));

        List<EnvironmentVariableEntity> currentVars = envVarStore.findAllByEnvironmentId(environmentId);

        Map<Long, EnvironmentVariableEntity> currentById = currentVars.stream()
                .collect(Collectors.toMap(EnvironmentVariableEntity::getId, Function.identity()));

        Set<String> currentKeys = currentVars.stream()
                .map(EnvironmentVariableEntity::getKey)
                .collect(Collectors.toSet());

        handleRemove(environmentId, currentKeys, currentById, command.getRemoved());
        handleUpdate(environmentId, currentKeys, currentById, command.getUpdated());
        handleAdd(environment, currentKeys, command.getAdded());
    }

    private void handleRemove(
            Long environmentId,
            Set<String> currentKeys,
            Map<Long, EnvironmentVariableEntity> currentById,
            List<Long> removedIds
    ) {
        if (removedIds == null || removedIds.isEmpty()) return;

        for (Long id : removedIds) {
            if (!currentById.containsKey(id)) {
                throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                        Map.of(Constants.REASON, "Variable id " + id + " does not belong to environment " + environmentId));
            }
        }

        for (Long id : removedIds) {
            EnvironmentVariableEntity entity = currentById.get(id);
            if (entity != null) {
                currentKeys.remove(entity.getKey());
                currentById.remove(id);
            }
        }

        envVarStore.deleteAllById(removedIds);
    }

    private void handleUpdate(
            Long environmentId,
            Set<String> currentKeys,
            Map<Long, EnvironmentVariableEntity> currentById,
            List<UpdateEnvironmentVariablesCommand.Update> updates
    ) {
        if (updates == null || updates.isEmpty()) return;

        List<EnvironmentVariableEntity> updatedEntities = new ArrayList<>();

        for (UpdateEnvironmentVariablesCommand.Update update : updates) {
            EnvironmentVariableEntity variable = currentById.get(update.getId());
            if (variable == null) {
                throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                        Map.of(Constants.REASON, "Variable id " + update.getId() + " does not belong to environment " + environmentId));
            }

            String oldKey = variable.getKey();
            String newKey = update.getKey();

            if (!Objects.equals(oldKey, newKey) && currentKeys.contains(newKey)) {
                throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                        Map.of(Constants.REASON, "Duplicate key: " + newKey));
            }

            variable.setKey(newKey);
            variable.setValue(update.getValue());
            variable.setActive(update.isActive());

            currentKeys.remove(oldKey);
            currentKeys.add(newKey);

            updatedEntities.add(variable);
        }

        envVarStore.saveAll(updatedEntities);
    }

    private void handleAdd(EnvironmentEntity environment, Set<String> currentKeys, List<UpdateEnvironmentVariablesCommand.Add> adds) {
        if (adds == null || adds.isEmpty()) return;

        List<EnvironmentVariableEntity> toSave = new ArrayList<>();

        for (UpdateEnvironmentVariablesCommand.Add add : adds) {
            if (currentKeys.contains(add.getKey())) {
                throw BusinessExceptionBuilder.exception(ErrorCode.BAD_REQUEST,
                        Map.of(Constants.REASON, "Duplicate key: " + add.getKey()));
            }

            EnvironmentVariableEntity entity = EnvironmentVariableEntity.builder()
                    .environment(environment)
                    .key(add.getKey())
                    .value(add.getValue())
                    .active(true)
                    .build();

            toSave.add(entity);
            currentKeys.add(add.getKey());
        }

        envVarStore.saveAll(toSave);
    }
}
