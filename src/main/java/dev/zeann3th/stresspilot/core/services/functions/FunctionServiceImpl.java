package dev.zeann3th.stresspilot.core.services.functions;

import dev.zeann3th.stresspilot.core.domain.entities.FunctionEntity;
import dev.zeann3th.stresspilot.core.ports.store.FunctionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FunctionServiceImpl implements FunctionService {
    private final FunctionStore functionStore;

    @Override
    public List<FunctionEntity> getAllFunctions() {
        return List.of();
    }
}
