package dev.zeann3th.stresspilot.core.services.impl;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ErrorCode;
import dev.zeann3th.stresspilot.core.domain.exception.BusinessExceptionBuilder;
import dev.zeann3th.stresspilot.core.ports.store.RunStore;
import dev.zeann3th.stresspilot.core.services.RunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RunServiceImpl implements RunService {
    private final RunStore runStore;

    @Override
    public List<RunEntity> getAllRuns(Long flowId) {
        if (flowId != null) {
            return runStore.findAllByFlowId(flowId);
        } else {
            return runStore.findAll();
        }
    }

    @Override
    public RunEntity getRunDetail(Long runId) {
        return runStore.findById(runId)
                .orElseThrow(() -> BusinessExceptionBuilder.exception(ErrorCode.RUN_NOT_FOUND));
    }
}
