package dev.zeann3th.stresspilot.core.services.functions;

import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;

import java.util.List;

public interface FunctionService {
    List<FunctionEntity> getAllFunctions();
}
