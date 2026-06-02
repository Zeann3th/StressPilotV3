package dev.zeann3th.stresspilot.core.utils;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlReportGeneratorTest {

    @Test
    void rendersDashboardAndSamplesDetailedLogsAcrossRun() throws Exception {
        RunReport report = RunReport.builder()
            .runId("run-1")
            .totalRequests(1000)
            .successCount(900)
            .failureCount(100)
            .successRate(90.0)
            .failureRate(10.0)
            .avgResponse(180.5)
            .p90(260.0)
            .p95(310.0)
            .durationSeconds(100.0)
            .tps(10.0)
            .endpointStats(List.of(
                EndpointStats.builder()
                    .endpointId(10L)
                    .endpointName("GET /api/orders")
                    .requests(1000)
                    .avgMs(180.5)
                    .p90Ms(260.0)
                    .p95Ms(310.0)
                    .build()
            ))
            .build();

        HtmlReportGenerator generator = new HtmlReportGenerator(report);
        generator.startDetailedLogs(1000);
        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 10, 0, 0);

        for (int i = 0; i < 1000; i++) {
            generator.appendLog(RequestLog.builder()
                .id((long) i + 1)
                .endpointId(10L)
                .endpointName("GET /api/orders")
                .statusCode(200)
                .responseTime(100L + i)
                .correlationId("corr-" + (i + 1))
                .activeThreads(i % 20)
                .request("request-" + (i + 1))
                .response("response-" + (i + 1))
                .createdAt(start.plusSeconds(i / 10))
                .build());
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        generator.writeTo(outputStream);
        String html = outputStream.toString();
        String logSection = html.substring(html.indexOf("Detailed Logs"));

        assertThat(html).contains("StressPilot Run Dashboard");
        assertThat(html).contains("Response Time Percentiles Over Time");
        assertThat(html).contains("Requests Per Second Over Time");
        assertThat(html).contains("Active Threads Over Time");
        assertThat(html).contains("Response Bandwidth Over Time");
        assertThat(html).contains("Request Summary");
        assertThat(logSection).contains("request-1");
        assertThat(logSection).contains("corr-1");
        assertThat(logSection).contains("request-499");
        assertThat(logSection).contains("request-1000");
        assertThat(countOccurrences(logSection, "<tr><td>")).isEqualTo(300);
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
