package dev.zeann3th.stresspilot.core.services;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;

import java.util.List;

public interface RunService {
    List<RunEntity> getAllRuns(Long flowId);

    RunEntity getRunDetail(Long runId);
}
