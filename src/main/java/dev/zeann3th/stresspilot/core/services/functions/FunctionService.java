package dev.zeann3th.stresspilot.core.services.functions;

import dev.zeann3th.stresspilot.core.domain.commands.function.CreateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.commands.function.UpdateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FunctionService {
    Page<FunctionEntity> getListFunction(String name, Pageable pageable);

    FunctionEntity getFunctionDetail(Long functionId);

    FunctionEntity createFunction(CreateFunctionCommand createFunctionCommand);

    FunctionEntity updateFunction(Long functionId, UpdateFunctionCommand updateFunctionCommand);

    void deleteFunction(Long functionId);

    List<FunctionEntity> getAllFunctions();
}
