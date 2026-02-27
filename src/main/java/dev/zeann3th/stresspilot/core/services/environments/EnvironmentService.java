package dev.zeann3th.stresspilot.core.services.environments;

import dev.zeann3th.stresspilot.core.domain.commands.environment.UpdateEnvironmentVariablesCommand;
import dev.zeann3th.stresspilot.core.domain.entities.EnvironmentVariableEntity;

import java.util.List;

public interface EnvironmentService {
    List<EnvironmentVariableEntity> getEnvironmentVariables(Long environmentId);

    void updateEnvironmentVariables(Long environmentId, UpdateEnvironmentVariablesCommand cmd);
}
