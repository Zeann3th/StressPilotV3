package dev.zeann3th.stresspilot.core.services.runs;

import java.util.List;

import dev.zeann3th.stresspilot.core.domain.commands.run.RunAnalysisDump;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.RunExportType;
import jakarta.servlet.http.HttpServletResponse;

public interface RunService {
    List<RunEntity> getRunHistory(Long flowId);

    RunEntity getRunDetail(String runId);

    RunEntity getLastRun(Long flowId);

    RunAnalysisDump getRunAnalysisDump(String runId);

    void exportRun(String runId, RunExportType type, HttpServletResponse response);

    void exportRunComparison(String runId1, String runId2, HttpServletResponse response);

    void interruptRun(String runId);
}
