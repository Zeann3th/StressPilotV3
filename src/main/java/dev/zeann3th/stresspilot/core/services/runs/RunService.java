package dev.zeann3th.stresspilot.core.services.runs;

import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

public interface RunService {
    List<RunEntity> getRunHistory(Long flowId);

    RunEntity getRunDetail(Long runId);

    RunReport generateReport(Long runId);

    RunEntity getLastRun(Long flowId);

    void exportRun(Long runId, String type, HttpServletResponse response);
}
