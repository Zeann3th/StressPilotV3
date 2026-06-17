package dev.zeann3th.stresspilot.core.utils.report.strategies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.enums.ReportElementType;
import dev.zeann3th.stresspilot.core.utils.report.ElementRenderContext;
import dev.zeann3th.stresspilot.core.utils.report.ReportElementRenderer;
import dev.zeann3th.stresspilot.core.utils.report.ReportTimeBucket;
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

import java.util.List;

@Component
public class LineChartElementRenderer implements ReportElementRenderer {

    private static final int CHART_ROWS = 20;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Override
    public ReportElementType supports() {
        return ReportElementType.LINE;
    }

    @Override
    public int render(SXSSFSheet sheet, int startRow, ElementRenderContext ctx) throws Exception {
        JsonNode config = objectMapper.readTree(ctx.getElement().getConfig());
        List<ReportTimeBucket> buckets = ctx.getTimeBuckets();

        // Write hidden data rows below chart anchor
        int dataStartRow = startRow + CHART_ROWS + 1;
        String[] timeLabels = buckets.stream().map(ReportTimeBucket::timeLabel).toArray(String[]::new);

        // Header row
        Row headerRow = sheet.createRow(dataStartRow);
        headerRow.createCell(0).setCellValue("Time");

        JsonNode seriesArr = config.path("series");
        int seriesCount = seriesArr.size();
        for (int i = 0; i < seriesCount; i++) {
            headerRow.createCell(i + 1).setCellValue(seriesArr.get(i).path("name").asText());
        }

        // Data rows
        for (int b = 0; b < buckets.size(); b++) {
            Row row = sheet.createRow(dataStartRow + 1 + b);
            row.createCell(0).setCellValue(timeLabels[b]);
            for (int s = 0; s < seriesCount; s++) {
                String expr = seriesArr.get(s).path("expression").asText();
                StandardEvaluationContext spelCtx = new StandardEvaluationContext();
                spelCtx.setVariable("bucket", buckets.get(b));
                Object val = spelParser.parseExpression(expr).getValue(spelCtx);
                double dVal = val instanceof Number n ? n.doubleValue() : 0.0;
                row.createCell(s + 1).setCellValue(dVal);
            }
        }

        // Pre-create rows for chart anchor area
        for (int r = startRow; r <= startRow + CHART_ROWS; r++) {
            if (sheet.getRow(r) == null) sheet.createRow(r);
        }

        // Create chart if we have data
        if (!buckets.isEmpty()) {
            sheet.createDrawingPatriarch();
            XSSFDrawing drawing = sheet.getDrawingPatriarch();
            XSSFChart chart = drawing.createChart(
                    drawing.createAnchor(0, 0, 0, 0, 0, startRow, 15, startRow + CHART_ROWS));
            chart.setTitleText(ctx.getElement().getName());
            chart.setTitleOverlay(false);

            XDDFCategoryAxis xAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            xAxis.setTitle(config.path("xAxisLabel").asText("Time"));
            XDDFValueAxis yAxis = chart.createValueAxis(AxisPosition.LEFT);
            yAxis.setTitle(config.path("yAxisLabel").asText("Value"));
            yAxis.setCrosses(AxisCrosses.AUTO_ZERO);

            int lastDataRow = dataStartRow + buckets.size();
            org.apache.poi.xssf.usermodel.XSSFSheet xssfSheet =
                    sheet.getWorkbook().getXSSFWorkbook().getSheet(sheet.getSheetName());

            XDDFCategoryDataSource cats = XDDFDataSourcesFactory.fromStringCellRange(
                    xssfSheet,
                    new CellRangeAddress(dataStartRow + 1, lastDataRow, 0, 0));

            XDDFLineChartData lineData = (XDDFLineChartData)
                    chart.createData(ChartTypes.LINE, xAxis, yAxis);

            for (int s = 0; s < seriesCount; s++) {
                XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(
                        xssfSheet,
                        new CellRangeAddress(dataStartRow + 1, lastDataRow, s + 1, s + 1));
                XDDFChartData.Series series = lineData.addSeries(cats, vals);
                series.setTitle(seriesArr.get(s).path("name").asText());
            }
            chart.plot(lineData);
        }

        return CHART_ROWS + 2 + 1 + buckets.size(); // chart rows + header + data + gap
    }
}
