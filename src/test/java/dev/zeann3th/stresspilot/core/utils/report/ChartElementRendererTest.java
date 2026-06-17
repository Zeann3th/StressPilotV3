package dev.zeann3th.stresspilot.core.utils.report;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.CustomReportElementEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ReportElementType;
import dev.zeann3th.stresspilot.core.utils.report.strategies.BarChartElementRenderer;
import dev.zeann3th.stresspilot.core.utils.report.strategies.LineChartElementRenderer;
import dev.zeann3th.stresspilot.core.utils.report.strategies.PieChartElementRenderer;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartElementRendererTest {

    private final LineChartElementRenderer lineRenderer = new LineChartElementRenderer();
    private final BarChartElementRenderer barRenderer = new BarChartElementRenderer();
    private final PieChartElementRenderer pieRenderer = new PieChartElementRenderer();

    @Test
    void lineRenderer_supports_LINE() {
        assertThat(lineRenderer.supports()).isEqualTo(ReportElementType.LINE);
    }

    @Test
    void barRenderer_supports_BAR() {
        assertThat(barRenderer.supports()).isEqualTo(ReportElementType.BAR);
    }

    @Test
    void pieRenderer_supports_PIE() {
        assertThat(pieRenderer.supports()).isEqualTo(ReportElementType.PIE);
    }

    @Test
    void lineRenderer_renders_without_throwing_when_timeBuckets_empty() throws Exception {
        SXSSFWorkbook wb = new SXSSFWorkbook();
        SXSSFSheet sheet = wb.createSheet("Test");

        CustomReportElementEntity element = CustomReportElementEntity.builder()
                .name("Response Time")
                .type(ReportElementType.LINE)
                .config("{\"xAxisLabel\":\"Time\",\"yAxisLabel\":\"ms\",\"series\":[{\"name\":\"Avg ms\",\"expression\":\"#bucket.avgMs\"}]}")
                .build();

        ElementRenderContext ctx = ElementRenderContext.builder()
                .report(RunReport.builder().build())
                .logs(List.of())
                .timeBuckets(List.of())
                .element(element)
                .build();

        int rows = lineRenderer.render(sheet, 0, ctx);
        assertThat(rows).isGreaterThan(0);
        wb.close();
    }

    @Test
    void barRenderer_renders_one_row_per_endpoint_stat() throws Exception {
        SXSSFWorkbook wb = new SXSSFWorkbook();
        SXSSFSheet sheet = wb.createSheet("Test");

        List<EndpointStats> stats = List.of(
                EndpointStats.builder().endpointName("Login").avgMs(45.0).build(),
                EndpointStats.builder().endpointName("Search").avgMs(120.0).build()
        );

        CustomReportElementEntity element = CustomReportElementEntity.builder()
                .name("Endpoint Avg")
                .type(ReportElementType.BAR)
                .config("{\"xAxisLabel\":\"Endpoint\",\"yAxisLabel\":\"Avg ms\",\"series\":[{\"name\":\"Avg ms\",\"expression\":\"#stat.avgMs\"}]}")
                .build();

        ElementRenderContext ctx = ElementRenderContext.builder()
                .report(RunReport.builder().endpointStats(stats).build())
                .logs(List.of())
                .timeBuckets(List.of())
                .element(element)
                .build();

        int rows = barRenderer.render(sheet, 0, ctx);
        assertThat(rows).isGreaterThan(0);
        wb.close();
    }

    @Test
    void pieRenderer_renders_slices_from_report() throws Exception {
        SXSSFWorkbook wb = new SXSSFWorkbook();
        SXSSFSheet sheet = wb.createSheet("Test");

        CustomReportElementEntity element = CustomReportElementEntity.builder()
                .name("Pass/Fail")
                .type(ReportElementType.PIE)
                .config("{\"slices\":[{\"label\":\"Pass\",\"expression\":\"#report.successCount\"},{\"label\":\"Fail\",\"expression\":\"#report.failureCount\"}]}")
                .build();

        ElementRenderContext ctx = ElementRenderContext.builder()
                .report(RunReport.builder().successCount(80).failureCount(20).build())
                .logs(List.of())
                .timeBuckets(List.of())
                .element(element)
                .build();

        int rows = pieRenderer.render(sheet, 0, ctx);
        assertThat(rows).isGreaterThan(0);
        wb.close();
    }
}
