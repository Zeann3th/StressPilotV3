package dev.zeann3th.stresspilot.core.utils;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RunComparisonExcelGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int WINDOW_SIZE = 1000;

    private final SXSSFWorkbook workbook;
    private final CellStyle headerStyle;
    private final CellStyle standardStyle;
    private final CellStyle numberStyle;
    private final CellStyle wrapStyle;
    private final RunReport left;
    private final RunReport right;
    private SXSSFSheet detailSheet;
    private int detailRowIdx;

    public RunComparisonExcelGenerator(RunReport left, RunReport right) {
        this.workbook = new SXSSFWorkbook(WINDOW_SIZE);
        this.workbook.setCompressTempFiles(false);
        this.headerStyle = createHeaderStyle();
        this.standardStyle = createBodyStyle(false);
        this.numberStyle = createBodyStyle(false);
        this.numberStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        this.wrapStyle = createBodyStyle(true);
        this.left = left;
        this.right = right;
    }

    public void writeComparisonSheets() {
        writeSummarySheet();
        writeEndpointSheet();
    }

    private void writeSummarySheet() {
        Sheet sheet = workbook.createSheet("Compare Summary");
        sheet.setDisplayGridlines(false);
        int row = 0;
        createHeaderRow(sheet, row++, "Metric", left.getRunId(), right.getRunId(), "Delta", "Delta %", "Better");
        row = metricRow(sheet, row, "Total Requests", left.getTotalRequests(), right.getTotalRequests(), false);
        row = metricRow(sheet, row, "Success Count", left.getSuccessCount(), right.getSuccessCount(), true);
        row = metricRow(sheet, row, "Failure Count", left.getFailureCount(), right.getFailureCount(), false);
        row = metricRow(sheet, row, "Pass Rate (%)", left.getSuccessRate(), right.getSuccessRate(), true);
        row = metricRow(sheet, row, "Fail Rate (%)", left.getFailureRate(), right.getFailureRate(), false);
        row = metricRow(sheet, row, "Average Response Time (ms)", left.getAvgResponse(), right.getAvgResponse(), false);
        row = metricRow(sheet, row, "P90 Response (ms)", left.getP90(), right.getP90(), false);
        row = metricRow(sheet, row, "P95 Response (ms)", left.getP95(), right.getP95(), false);
        row = metricRow(sheet, row, "RPS", left.getTps(), right.getTps(), true);

        setColumnWidths(sheet, 26, 18, 18, 18, 18, 22);
    }

    private void writeEndpointSheet() {
        Sheet sheet = workbook.createSheet("Endpoint Delta");
        sheet.setDisplayGridlines(false);
        int row = 0;
        createHeaderRow(sheet, row++, "Endpoint ID", "Endpoint Name", "Run A Requests", "Run B Requests",
                "Avg A", "Avg B", "Avg Delta", "P95 A", "P95 B", "P95 Delta");

        Map<Long, EndpointStats> leftById = endpointMap(left);
        Map<Long, EndpointStats> rightById = endpointMap(right);
        TreeSet<Long> endpointIds = new TreeSet<>();
        endpointIds.addAll(leftById.keySet());
        endpointIds.addAll(rightById.keySet());

        for (Long endpointId : endpointIds) {
            EndpointStats a = leftById.get(endpointId);
            EndpointStats b = rightById.get(endpointId);
            Row data = sheet.createRow(row++);
            String endpointName = firstNonBlank(
                    a != null ? a.getEndpointName() : null,
                    b != null ? b.getEndpointName() : null,
                    "Endpoint-" + endpointId);
            createCell(data, 0, endpointId, standardStyle);
            createCell(data, 1, endpointName, standardStyle);
            createCell(data, 2, a != null ? a.getRequests() : 0, standardStyle);
            createCell(data, 3, b != null ? b.getRequests() : 0, standardStyle);
            createCell(data, 4, value(a != null ? a.getAvgMs() : null), numberStyle);
            createCell(data, 5, value(b != null ? b.getAvgMs() : null), numberStyle);
            createCell(data, 6, value(b != null ? b.getAvgMs() : null) - value(a != null ? a.getAvgMs() : null), numberStyle);
            createCell(data, 7, value(a != null ? a.getP95Ms() : null), numberStyle);
            createCell(data, 8, value(b != null ? b.getP95Ms() : null), numberStyle);
            createCell(data, 9, value(b != null ? b.getP95Ms() : null) - value(a != null ? a.getP95Ms() : null), numberStyle);
        }

        setColumnWidths(sheet, 14, 28, 16, 16, 14, 14, 14, 14, 14, 14);
    }

    public void startRunDetailSheet(String sheetName) {
        detailSheet = workbook.createSheet(sheetName);
        detailSheet.setDisplayGridlines(false);
        detailRowIdx = 0;
        createHeaderRow(detailSheet, detailRowIdx++, "ID", "Correlation ID", "Endpoint ID", "Endpoint Name",
                "Status", "Response Time (ms)", "Request", "Response", "Timestamp");
        detailSheet.setColumnWidth(6, 80 * 256);
        detailSheet.setColumnWidth(7, 80 * 256);
    }

    public void appendRunDetailRow(RequestLog log) {
        if (detailSheet == null) {
            startRunDetailSheet("Run Logs");
        }
        Row row = detailSheet.createRow(detailRowIdx++);
        createCell(row, 0, log.getId(), standardStyle);
        createCell(row, 1, log.getCorrelationId(), standardStyle);
        createCell(row, 2, log.getEndpointId(), standardStyle);
        createCell(row, 3, log.getEndpointName(), standardStyle);
        createCell(row, 4, log.getStatusCode(), standardStyle);
        createCell(row, 5, log.getResponseTime(), numberStyle);
        createCell(row, 6, truncateText(log.getRequest()), wrapStyle);
        createCell(row, 7, truncateText(log.getResponse()), wrapStyle);
        createCell(row, 8, log.getCreatedAt() != null ? log.getCreatedAt().format(DATE_FORMATTER) : "", standardStyle);
    }

    public void export(HttpServletResponse response) throws IOException {
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            workbook.write(outputStream);
        } finally {
            workbook.close();
        }
    }

    private int metricRow(Sheet sheet, int rowIndex, String metric, Number leftValue, Number rightValue, boolean higherIsBetter) {
        double a = value(leftValue);
        double b = value(rightValue);
        double delta = b - a;
        double deltaPct = a == 0 ? 0 : (delta / a) * 100.0;
        String better = Objects.equals(a, b) ? "Tie" : ((higherIsBetter ? b > a : b < a) ? right.getRunId() : left.getRunId());

        Row row = sheet.createRow(rowIndex);
        createCell(row, 0, metric, standardStyle);
        createCell(row, 1, a, numberStyle);
        createCell(row, 2, b, numberStyle);
        createCell(row, 3, delta, numberStyle);
        createCell(row, 4, deltaPct, numberStyle);
        createCell(row, 5, better, standardStyle);
        return rowIndex + 1;
    }

    private Map<Long, EndpointStats> endpointMap(RunReport report) {
        if (report.getEndpointStats() == null) {
            return new LinkedHashMap<>();
        }
        return report.getEndpointStats().stream()
                .filter(stat -> stat.getEndpointId() != null)
                .collect(Collectors.toMap(EndpointStats::getEndpointId, Function.identity(), (a, _) -> a, LinkedHashMap::new));
    }

    private void createHeaderRow(Sheet sheet, int rowIndex, String... headers) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < headers.length; i++) {
            createCell(row, i, headers[i], headerStyle);
        }
    }

    private void setColumnWidths(Sheet sheet, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
    }

    private void createCell(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof Number num) {
            cell.setCellValue(num.doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private CellStyle createHeaderStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createBodyStyle(boolean wrap) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(wrap);
        return style;
    }

    private double value(Number value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private String truncateText(String text) {
        if (text == null) return "";
        return text.length() > 32767 ? text.substring(0, 32767) : text;
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return fallback;
    }
}
