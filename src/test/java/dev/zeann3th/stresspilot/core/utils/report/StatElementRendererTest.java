package dev.zeann3th.stresspilot.core.utils.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import dev.zeann3th.stresspilot.core.domain.entities.CustomReportElementEntity;
import dev.zeann3th.stresspilot.core.domain.enums.ReportElementType;
import dev.zeann3th.stresspilot.core.utils.report.strategies.StatElementRenderer;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatElementRendererTest {

    private final StatElementRenderer renderer = new StatElementRenderer(new ObjectMapper());

    @Test
    void supports_STAT() {
        assertThat(renderer.supports()).isEqualTo(ReportElementType.STAT);
    }

    @Test
    void renders_stat_panel_with_label_and_value() throws Exception {
        SXSSFWorkbook wb = new SXSSFWorkbook();
        SXSSFSheet sheet = wb.createSheet("Test");

        RunReport report = RunReport.builder()
                .successRate(87.5)
                .totalRequests(100)
                .build();

        CustomReportElementEntity element = CustomReportElementEntity.builder()
                .name("Pass Rate")
                .type(ReportElementType.STAT)
                .config("{\"expression\":\"#report.successRate\",\"label\":\"Pass Rate\",\"unit\":\"%\"}")
                .build();

        ElementRenderContext ctx = ElementRenderContext.builder()
                .report(report)
                .logs(List.of())
                .timeBuckets(List.of())
                .element(element)
                .build();

        int rowsConsumed = renderer.render(sheet, 0, ctx);

        assertThat(rowsConsumed).isGreaterThan(0);
        assertThat((Object) sheet.getRow(0)).isNotNull();
        assertThat((String) sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Pass Rate (%)");
        assertThat(sheet.getRow(1).getCell(0).getNumericCellValue()).isEqualTo(87.5);
        wb.close();
    }

    @Test
    void bad_spel_expression_throws_exception_not_silently_fails() {
        SXSSFWorkbook wb = new SXSSFWorkbook();
        SXSSFSheet sheet = wb.createSheet("Test");

        CustomReportElementEntity element = CustomReportElementEntity.builder()
                .name("Bad")
                .type(ReportElementType.STAT)
                .config("{\"expression\":\"#report.nonExistentField.call()\",\"label\":\"Bad\"}")
                .build();

        ElementRenderContext ctx = ElementRenderContext.builder()
                .report(RunReport.builder().build())
                .logs(List.of())
                .timeBuckets(List.of())
                .element(element)
                .build();

        // renderer.render throws — caller (CustomSheetBuilder) catches and writes error row
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> renderer.render(sheet, 0, ctx));
        try { wb.close(); } catch (Exception ignored) {}
    }
}
