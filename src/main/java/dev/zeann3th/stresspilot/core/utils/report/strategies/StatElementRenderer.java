package dev.zeann3th.stresspilot.core.utils.report.strategies;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.zeann3th.stresspilot.core.domain.enums.ReportElementType;
import dev.zeann3th.stresspilot.core.utils.report.ElementRenderContext;
import dev.zeann3th.stresspilot.core.utils.report.ReportElementRenderer;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Component
public class StatElementRenderer implements ReportElementRenderer {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Override
    public ReportElementType supports() {
        return ReportElementType.STAT;
    }

    @Override
    public int render(SXSSFSheet sheet, int startRow, ElementRenderContext ctx) throws Exception {
        JsonNode config = objectMapper.readTree(ctx.getElement().getConfig());
        String expression = config.path("expression").asText();
        String label = config.has("label") ? config.path("label").asText()
                : ctx.getElement().getName();
        String unit = config.has("unit") ? config.path("unit").asText() : "";

        StandardEvaluationContext spelCtx = new StandardEvaluationContext();
        spelCtx.setVariable("report", ctx.getReport());
        spelCtx.setVariable("logs", ctx.getLogs());
        spelCtx.setVariable("stats", ctx.getEndpointStats());

        Object rawValue = spelParser.parseExpression(expression).getValue(spelCtx);
        double value = rawValue instanceof Number n ? n.doubleValue() : 0.0;

        // Row 0: label (with unit if present)
        Row labelRow = sheet.createRow(startRow);
        Cell labelCell = labelRow.createCell(0);
        labelCell.setCellValue(label + (unit.isEmpty() ? "" : " (" + unit + ")"));
        CellStyle bold = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        bold.setFont(font);
        labelCell.setCellStyle(bold);

        // Row 1: value
        Row valueRow = sheet.createRow(startRow + 1);
        valueRow.createCell(0).setCellValue(value);

        return 3; // label + value + gap row
    }
}
