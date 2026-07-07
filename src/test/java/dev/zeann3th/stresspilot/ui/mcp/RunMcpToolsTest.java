package dev.zeann3th.stresspilot.ui.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class RunMcpToolsTest {

    @Test
    void runAnalysisDumpIsNotExposedAsMcpTool() {
        assertThat(Arrays.stream(RunMcpTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .map(method -> method.getName())
                .toList())
                .contains("getAllRuns", "getRunDetail", "getLastRun", "interruptRun")
                .doesNotContain("getRunAnalysisDump");
    }
}
