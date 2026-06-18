package dev.zeann3th.stresspilot.core.utils.report.strategies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.enums.ReportElementType;
import dev.zeann3th.stresspilot.core.utils.report.ElementRenderContext;
import dev.zeann3th.stresspilot.core.utils.report.ReportElementRenderer;
import dev.zeann3th.stresspilot.core.utils.report.SafeSpelContextFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BarChartElementRenderer implements ReportElementRenderer {

    private static final int CHART_ROWS = 20;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Override
    public ReportElementType supports() {
        return ReportElementType.BAR;
    }

    @Override
    public int render(SXSSFSheet sheet, int startRow, ElementRenderContext ctx) throws Exception {
        JsonNode config = objectMapper.readTree(ctx.getElement().getConfig());
        List<EndpointStats> stats = ctx.getEndpointStats();

        int dataStartRow = startRow + CHART_ROWS + 1;
        JsonNode seriesArr = config.path("series");
        int seriesCount = seriesArr.size();

        // Header row
        Row headerRow = sheet.createRow(dataStartRow);
        headerRow.createCell(0).setCellValue(config.path("xAxisLabel").asText("Category"));
        for (int i = 0; i < seriesCount; i++) {
            headerRow.createCell(i + 1).setCellValue(seriesArr.get(i).path("name").asText());
        }

        // Data rows — one per endpoint stat
        for (int idx = 0; idx < stats.size(); idx++) {
            EndpointStats stat = stats.get(idx);
            Row row = sheet.createRow(dataStartRow + 1 + idx);
            String label = stat.getEndpointName() != null
                    ? stat.getEndpointName()
                    : "Endpoint " + stat.getEndpointId();
            row.createCell(0).setCellValue(label);
            for (int s = 0; s < seriesCount; s++) {
                String expr = seriesArr.get(s).path("expression").asText();
                EvaluationContext spelCtx = SafeSpelContextFactory.createForStat(stat);
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
        if (!stats.isEmpty()) {
            sheet.createDrawingPatriarch();
            XSSFDrawing drawing = sheet.getDrawingPatriarch();
            XSSFChart chart = drawing.createChart(
                    drawing.createAnchor(0, 0, 0, 0, 0, startRow, 15, startRow + CHART_ROWS));
            chart.setTitleText(ctx.getElement().getName());
            chart.setTitleOverlay(false);

            XDDFCategoryAxis xAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
            xAxis.setTitle(config.path("xAxisLabel").asText("Category"));
            XDDFValueAxis yAxis = chart.createValueAxis(AxisPosition.LEFT);
            yAxis.setTitle(config.path("yAxisLabel").asText("Value"));
            yAxis.setCrosses(AxisCrosses.AUTO_ZERO);

            int lastDataRow = dataStartRow + stats.size();
            org.apache.poi.xssf.usermodel.XSSFSheet xssfSheet =
                    sheet.getWorkbook().getXSSFWorkbook().getSheet(sheet.getSheetName());

            XDDFCategoryDataSource cats = XDDFDataSourcesFactory.fromStringCellRange(
                    xssfSheet,
                    new CellRangeAddress(dataStartRow + 1, lastDataRow, 0, 0));

            XDDFBarChartData barData = (XDDFBarChartData)
                    chart.createData(ChartTypes.BAR, xAxis, yAxis);
            barData.setBarDirection(BarDirection.COL);

            for (int s = 0; s < seriesCount; s++) {
                XDDFNumericalDataSource<Double> vals = XDDFDataSourcesFactory.fromNumericCellRange(
                        xssfSheet,
                        new CellRangeAddress(dataStartRow + 1, lastDataRow, s + 1, s + 1));
                XDDFChartData.Series series = barData.addSeries(cats, vals);
                series.setTitle(seriesArr.get(s).path("name").asText());
            }
            chart.plot(barData);
        }

        return CHART_ROWS + 2 + 1 + stats.size();
    }
}
