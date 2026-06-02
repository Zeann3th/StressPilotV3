package dev.zeann3th.stresspilot.core.utils;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTLineSer;
import org.openxmlformats.schemas.drawingml.x2006.chart.STMarkerStyle;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelGeneratorTest {

    @Test
    void writesTimeSeriesLineChartsFromDetailedLogsWithoutRemovingTables() throws Exception {
        RunReport report = RunReport.builder()
            .runId("run-1")
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

        MockHttpServletResponse response = new MockHttpServletResponse();
        ExcelGenerator generator = new ExcelGenerator().writeSummarySheets(report);

        LocalDateTime start = LocalDateTime.of(2026, 6, 1, 10, 0, 0);
        generator.startDetailedSheet();
        generator.appendDetailRow(log(1L, 10L, "GET /api/orders", 100L, start));
        generator.appendDetailRow(log(2L, 20L, "POST /api/payments", 180L, start));
        generator.appendDetailRow(log(3L, 10L, "GET /api/orders", 140L, start.plusSeconds(1)));
        generator.appendDetailRow(log(4L, 20L, "POST /api/payments", 260L, start.plusSeconds(1)));

        generator.export(response);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            XSSFSheet summary = workbook.getSheet("Summary");
            XSSFSheet endpoints = workbook.getSheet("Endpoint Aggregates");
            XSSFSheet charts = workbook.getSheet("Charts");

            assertThat(summary).isNotNull();
            assertThat(endpoints).isNotNull();
            assertThat(charts).isNotNull();
            assertThat(workbook.getSheetIndex(charts)).isEqualTo(3);
            assertThat(summary.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Run ID");
            assertThat(summary.getRow(9).getCell(0).getStringCellValue()).isEqualTo("RPS");
            assertThat(endpoints.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Endpoint ID");
            assertThat(endpoints.getRow(0).getCell(6).getStringCellValue()).isEqualTo("RPS");
            assertThat(charts.getRow(0).getCell(0).getStringCellValue()).isEqualTo("StressPilot Run Dashboard");
            assertThat(workbook.getSheet("Detailed Logs").getRow(1).getCell(1).getStringCellValue()).isEqualTo("corr-1");

            assertThat(chartCount(summary)).isEqualTo(1);
            assertThat(pieChartCount(summary)).isEqualTo(1);
            assertThat(chartCount(endpoints)).isZero();
            assertThat(chartCount(charts)).isEqualTo(7);
            assertThat(lineChartCount(charts)).isEqualTo(7);
            assertThat(allLineSeriesAreSmoothAndMarkerless(charts)).isTrue();
        }
    }

    private RequestLog log(Long id, Long endpointId, String endpointName, Long responseTime, LocalDateTime createdAt) {
        return RequestLog.builder()
            .id(id)
            .endpointId(endpointId)
            .endpointName(endpointName)
            .statusCode(200)
            .responseTime(responseTime)
            .correlationId("corr-" + id)
            .activeThreads(2)
            .request("{}")
            .response("{}")
            .createdAt(createdAt)
            .build();
    }

    private int chartCount(XSSFSheet sheet) {
        XSSFDrawing drawing = sheet.getDrawingPatriarch();
        return drawing == null ? 0 : drawing.getCharts().size();
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

    private int pieChartCount(XSSFSheet sheet) {
        XSSFDrawing drawing = sheet.getDrawingPatriarch();
        if (drawing == null) {
            return 0;
        }
        return drawing.getCharts().stream()
            .mapToInt(chart -> chart.getCTChart().getPlotArea().sizeOfPieChartArray())
            .sum();
    }

    private boolean allLineSeriesAreSmoothAndMarkerless(XSSFSheet sheet) {
        XSSFDrawing drawing = sheet.getDrawingPatriarch();
        if (drawing == null) {
            return false;
        }
        return drawing.getCharts().stream()
            .flatMap(chart -> chart.getCTChart().getPlotArea().getLineChartList().stream())
            .flatMap(lineChart -> lineChart.getSerList().stream())
            .allMatch(this::isSmoothAndMarkerless);
    }

    private boolean isSmoothAndMarkerless(CTLineSer series) {
        return series.isSetSmooth()
            && series.getSmooth().getVal()
            && series.isSetMarker()
            && series.getMarker().isSetSymbol()
            && series.getMarker().getSymbol().getVal() == STMarkerStyle.NONE;
    }
}
