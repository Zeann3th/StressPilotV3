package dev.zeann3th.stresspilot.ui.mcp;

import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunAnalysisDump;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunAnalysisMetadata;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import dev.zeann3th.stresspilot.core.domain.enums.RunExportType;
import dev.zeann3th.stresspilot.core.services.runs.RunService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunMcpToolsTest {

    @Test
    void getRunAnalysisDumpReturnsFullServiceDump() {
        RunAnalysisDump expected = RunAnalysisDump.builder()
                .run(RunAnalysisMetadata.builder().id("run-1").build())
                .report(RunReport.builder().runId("run-1").totalRequests(1).build())
                .logCount(1)
                .logs(List.of(RequestLog.builder()
                        .id(1L)
                        .request("request-body")
                        .response("response-body")
                        .build()))
                .build();
        FakeRunService runService = new FakeRunService(expected);
        RunMcpTools tools = new RunMcpTools(runService);

        RunAnalysisDump actual = tools.getRunAnalysisDump("run-1");

        assertThat(actual).isSameAs(expected);
        assertThat(actual.getLogs()).hasSize(1);
        assertThat(actual.getLogs().getFirst().getRequest()).isEqualTo("request-body");
        assertThat(actual.getLogs().getFirst().getResponse()).isEqualTo("response-body");
        assertThat(runService.requestedRunId).isEqualTo("run-1");
    }

    private static final class FakeRunService implements RunService {
        private final RunAnalysisDump dump;
        private String requestedRunId;

        private FakeRunService(RunAnalysisDump dump) {
            this.dump = dump;
        }

        @Override
        public List<RunEntity> getRunHistory(Long flowId) {
            return List.of();
        }

        @Override
        public RunEntity getRunDetail(String runId) {
            return RunEntity.builder().id(runId).build();
        }

        @Override
        public RunEntity getLastRun(Long flowId) {
            return null;
        }

        @Override
        public RunAnalysisDump getRunAnalysisDump(String runId) {
            requestedRunId = runId;
            return dump;
        }

        @Override
        public void exportRun(String runId, RunExportType type, HttpServletResponse response) {
        }

        @Override
        public void exportRunComparison(String runId1, String runId2, HttpServletResponse response) {
        }

        @Override
        public void interruptRun(String runId) {
        }
    }
}
