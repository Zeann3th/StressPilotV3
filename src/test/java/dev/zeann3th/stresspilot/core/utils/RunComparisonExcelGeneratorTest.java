package dev.zeann3th.stresspilot.core.utils;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunComparisonExcelGeneratorTest {

    @Test
    void writesComparisonChartsWithBothRunsInEachChart() throws Exception {
        RunReport left = report("run-a");
        RunReport right = report("run-b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RunComparisonExcelGenerator generator = new RunComparisonExcelGenerator(left, right);

        generator.writeComparisonSheets();
        generator.startRunDetailSheet("Run A Logs");
        generator.appendRunDetailRow(log(1L, 10L, "GET /api/orders", 100L, 2, start()));
        generator.appendRunDetailRow(log(2L, 20L, "POST /api/payments", 180L, 2, start()));
        generator.appendRunDetailRow(log(3L, 10L, "GET /api/orders", 130L, 2, start().plusSeconds(1)));
        generator.appendRunDetailRow(log(4L, 20L, "POST /api/payments", 250L, 2, start().plusSeconds(1)));
        generator.startRunDetailSheet("Run B Logs");
        generator.appendRunDetailRow(log(5L, 10L, "GET /api/orders", 90L, 3, start()));
        generator.appendRunDetailRow(log(6L, 20L, "POST /api/payments", 160L, 3, start()));
        generator.appendRunDetailRow(log(7L, 10L, "GET /api/orders", 120L, 3, start().plusSeconds(1)));
        generator.appendRunDetailRow(log(8L, 20L, "POST /api/payments", 210L, 3, start().plusSeconds(1)));

        generator.export(response);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            XSSFSheet charts = workbook.getSheet("Charts");

            assertThat(charts).isNotNull();
            assertThat(charts.getRow(0).getCell(0).getStringCellValue()).isEqualTo("StressPilot Comparison Dashboard");
            assertThat(lineChartCount(charts)).isEqualTo(7);
            assertThat(seriesTitles(charts)).contains("run-a - Avg ms", "run-b - Avg ms");
        }
    }

    private RunReport report(String runId) {
        return RunReport.builder()
                .runId(runId)
                .totalRequests(240)
                .successCount(220)
                .failureCount(20)
                .successRate(91.67)
                .failureRate(8.33)
                .avgResponse(180.5)
                .p90(260.0)
                .p95(310.0)
                .durationSeconds(60.0)
                .tps(4.0)
                .endpointStats(List.of(
                        EndpointStats.builder()
                                .endpointId(10L)
                                .endpointName("GET /api/orders")
                                .requests(120)
                                .avgMs(120.0)
                                .p90Ms(190.0)
                                .p95Ms(230.0)
                                .build(),
                        EndpointStats.builder()
                                .endpointId(20L)
                                .endpointName("POST /api/payments")
                                .requests(120)
                                .avgMs(241.0)
                                .p90Ms(330.0)
                                .p95Ms(410.0)
                                .build()
                ))
                .build();
    }

    private RequestLog log(Long id, Long endpointId, String endpointName, Long responseTime, Integer activeThreads, LocalDateTime createdAt) {
        return RequestLog.builder()
                .id(id)
                .endpointId(endpointId)
                .endpointName(endpointName)
                .statusCode(200)
                .responseTime(responseTime)
                .activeThreads(activeThreads)
                .request("{}")
                .response("{}")
                .createdAt(createdAt)
                .build();
    }

    private LocalDateTime start() {
        return LocalDateTime.of(2026, 6, 1, 10, 0, 0);
    }

    private int lineChartCount(XSSFSheet sheet) {
        XSSFDrawing drawing = sheet.getDrawingPatriarch();
        if (drawing == null) {
            return 0;
        }
        return drawing.getCharts().stream()
                .mapToInt(chart -> chart.getCTChart().getPlotArea().sizeOfLineChartArray())
                .sum();
    }

    private List<String> seriesTitles(XSSFSheet sheet) {
        XSSFDrawing drawing = sheet.getDrawingPatriarch();
        if (drawing == null) {
            return List.of();
        }
        return drawing.getCharts().stream()
                .flatMap(chart -> chart.getCTChart().getPlotArea().getLineChartList().stream())
                .flatMap(lineChart -> lineChart.getSerList().stream())
                .map(series -> {
                    if (series.getTx().isSetStrRef()) {
                        return series.getTx().getStrRef().getStrCache().getPtArray(0).getV();
                    }
                    return series.getTx().getV();
                })
                .toList();
    }
}
