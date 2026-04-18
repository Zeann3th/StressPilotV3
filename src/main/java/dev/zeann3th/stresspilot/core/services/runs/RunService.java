package dev.zeann3th.stresspilot.core.services.runs;

import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface RunService {
    List<RunEntity> getRunHistory(Long flowId);

    RunEntity getRunDetail(String runId);

    RunEntity getLastRun(Long flowId);

    void exportRun(String runId, HttpServletResponse response);

    void interruptRun(String runId);
}
