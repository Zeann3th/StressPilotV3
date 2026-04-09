package dev.zeann3th.stresspilot.core.services.functions;

import dev.zeann3th.stresspilot.core.domain.commands.function.CreateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.commands.function.UpdateFunctionCommand;
import dev.zeann3th.stresspilot.core.domain.constants.Constants;
import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.CommandExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.FunctionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FunctionServiceImpl implements FunctionService {
    private final FunctionStore functionStore;

    @Override
    public Page<FunctionEntity> getListFunction(String name, Pageable pageable) {
        return functionStore.findAll(name, pageable);
    }

    @Override
    public FunctionEntity getFunctionDetail(Long functionId) {
        return functionStore.findById(functionId)
                .orElseThrow(() -> CommandExceptionBuilder.exception(ErrorCode.ER0025, Map.of(Constants.ID,functionId.toString())));
    }

    @Override
    public FunctionEntity createFunction(CreateFunctionCommand createFunctionCommand) {
        FunctionEntity entity = FunctionEntity.builder()
                .name(createFunctionCommand.getName())
                .body(createFunctionCommand.getBody())
                .description(createFunctionCommand.getDescription())
                .active(true)
                .build();
        return functionStore.save(entity);
    }

    @Override
    public FunctionEntity updateFunction(Long functionId, UpdateFunctionCommand updateFunctionCommand) {
        FunctionEntity entity = getFunctionDetail(functionId);
        if (updateFunctionCommand.getName() != null) entity.setName(updateFunctionCommand.getName());
        if (updateFunctionCommand.getBody() != null) entity.setBody(updateFunctionCommand.getBody());
        if (updateFunctionCommand.getDescription() != null) entity.setDescription(updateFunctionCommand.getDescription());
        return functionStore.save(entity);
    }

    @Override
    public void deleteFunction(Long functionId) {
        if (functionStore.findById(functionId).isEmpty()) {
            throw CommandExceptionBuilder.exception(ErrorCode.ER0025, Map.of(Constants.ID,functionId.toString()));
        }
        functionStore.deleteById(functionId);
    }

    @Override
    public List<FunctionEntity> getAllFunctions() {
        return functionStore.findAll();
    }
}
