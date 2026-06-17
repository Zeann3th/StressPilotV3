package dev.zeann3th.stresspilot.core.utils.report.strategies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.enums.ReportElementType;
import dev.zeann3th.stresspilot.core.utils.report.ElementRenderContext;
import dev.zeann3th.stresspilot.core.utils.report.ReportElementRenderer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Component
public class PieChartElementRenderer implements ReportElementRenderer {

    private static final int CHART_ROWS = 20;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Override
    public ReportElementType supports() {
        return ReportElementType.PIE;
    }

    @Override
    public int render(SXSSFSheet sheet, int startRow, ElementRenderContext ctx) throws Exception {
        JsonNode config = objectMapper.readTree(ctx.getElement().getConfig());
        JsonNode slices = config.path("slices");

        int dataStartRow = startRow + CHART_ROWS + 1;

        // Header row
        Row headerRow = sheet.createRow(dataStartRow);
        headerRow.createCell(0).setCellValue("Label");
        headerRow.createCell(1).setCellValue("Value");

        // Build SpEL context with report, logs, stats variables
        StandardEvaluationContext spelCtx = new StandardEvaluationContext();
        spelCtx.setVariable("report", ctx.getReport());
        spelCtx.setVariable("logs", ctx.getLogs());
        spelCtx.setVariable("stats", ctx.getEndpointStats());

        // Data rows — one per slice
        for (int i = 0; i < slices.size(); i++) {
            JsonNode slice = slices.get(i);
            String label = slice.path("label").asText("Slice " + i);
            String expr = slice.path("expression").asText();
            Object val = spelParser.parseExpression(expr).getValue(spelCtx);
            double dVal = val instanceof Number n ? n.doubleValue() : 0.0;

            Row row = sheet.createRow(dataStartRow + 1 + i);
            row.createCell(0).setCellValue(label);
            row.createCell(1).setCellValue(dVal);
        }

        // Pre-create rows for chart anchor area
        for (int r = startRow; r <= startRow + CHART_ROWS; r++) {
            if (sheet.getRow(r) == null) sheet.createRow(r);
        }

        // Create chart if we have slices defined
        if (slices.size() > 0) {
            sheet.createDrawingPatriarch();
            XSSFDrawing drawing = sheet.getDrawingPatriarch();
            XSSFChart chart = drawing.createChart(
                    drawing.createAnchor(0, 0, 0, 0, 0, startRow, 15, startRow + CHART_ROWS));
            chart.setTitleText(ctx.getElement().getName());
            chart.setTitleOverlay(false);

            int lastDataRow = dataStartRow + slices.size();
            org.apache.poi.xssf.usermodel.XSSFSheet xssfSheet =
                    sheet.getWorkbook().getXSSFWorkbook().getSheet(sheet.getSheetName());

            XDDFCategoryDataSource cats = XDDFDataSourcesFactory.fromStringCellRange(
                    xssfSheet,
                    new CellRangeAddress(dataStartRow + 1, lastDataRow, 0, 0));
            XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(
                    xssfSheet,
                    new CellRangeAddress(dataStartRow + 1, lastDataRow, 1, 1));

            XDDFPieChartData pieData = (XDDFPieChartData)
                    chart.createData(ChartTypes.PIE, null, null);
            pieData.addSeries(cats, vals);
            chart.plot(pieData);
        }

        return CHART_ROWS + 2 + 1 + slices.size();
    }
}
