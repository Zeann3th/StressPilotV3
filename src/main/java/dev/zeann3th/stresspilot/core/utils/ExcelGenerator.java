package dev.zeann3th.stresspilot.core.utils;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Slf4j(topic = "ExcelGenerator")
public class ExcelGenerator {

    private static final int WINDOW_SIZE = 1000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SXSSFWorkbook workbook;

    private final CellStyle headerStyle;
    private final CellStyle numberStyle;
    private final CellStyle intStyle;
    private final CellStyle wrapStyle;
    private final CellStyle standardStyle;

    private SXSSFSheet detailsSheet;
    private int detailsRowIdx = 0;

    private final int[] maxColumnWidths = new int[8];

    public ExcelGenerator() {
        this.workbook = new SXSSFWorkbook(WINDOW_SIZE);
        this.workbook.setCompressTempFiles(false);

        this.headerStyle = createHeaderStyle();

        this.numberStyle = createBodyStyle(false);
        this.numberStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));

        this.intStyle = createBodyStyle(false);
        this.intStyle.setDataFormat(workbook.createDataFormat().getFormat("0"));

        this.wrapStyle = createBodyStyle(true);
        this.standardStyle = createBodyStyle(false);
    }

    public ExcelGenerator writeSummarySheets(RunReport report) {
        log.info("Starting ultra-fast Excel generation for Run ID: {}", report.getRunId());

        // --- SUMMARY SHEET ---
        SXSSFSheet summary = workbook.createSheet("Summary");
        int r = 0;
        addRow(summary, r++, "Run ID", report.getRunId(), headerStyle, standardStyle);
        addRow(summary, r++, "Total Requests", report.getTotalRequests(), headerStyle, intStyle);
        addRow(summary, r++, "Success Count", report.getSuccessCount(), headerStyle, intStyle);
        addRow(summary, r++, "Failure Count", report.getFailureCount(), headerStyle, intStyle);
        addRow(summary, r++, "Success Rate (%)", report.getSuccessRate(), headerStyle, numberStyle);
        addRow(summary, r++, "Average Response Time (ms)", report.getAvgResponse(), headerStyle, numberStyle);
        addRow(summary, r++, "P90 Response (ms)", report.getP90(), headerStyle, numberStyle);
        addRow(summary, r++, "TPS", report.getTps(), headerStyle, numberStyle);

        summary.setColumnWidth(0, 30 * 256);
        summary.setColumnWidth(1, 25 * 256);

        // --- ENDPOINTS SHEET ---
        if (report.getEndpointStats() != null && !report.getEndpointStats().isEmpty()) {
            SXSSFSheet endpointsSheet = workbook.createSheet("Endpoint Aggregates");
            Row head = endpointsSheet.createRow(0);
            String[] eHeaders = {"Endpoint ID", "Endpoint Name", "Requests", "Avg (ms)", "P90 (ms)", "P95 (ms)"};
            for (int i = 0; i < eHeaders.length; i++) {
                createCell(head, i, eHeaders[i], headerStyle);
            }

            int er = 1;
            for (EndpointStats stat : report.getEndpointStats()) {
                Row rr = endpointsSheet.createRow(er++);
                createCell(rr, 0, stat.getEndpointId(), intStyle);
                createCell(rr, 1, stat.getEndpointName() != null ? stat.getEndpointName() : "Unknown", wrapStyle);
                createCell(rr, 2, stat.getRequests(), intStyle);
                createCell(rr, 3, stat.getAvgMs(), numberStyle);
                createCell(rr, 4, stat.getP90Ms(), numberStyle);
                createCell(rr, 5, stat.getP95Ms(), numberStyle);
            }

            endpointsSheet.setColumnWidth(0, 15 * 256);
            endpointsSheet.setColumnWidth(1, 40 * 256);
            endpointsSheet.setColumnWidth(2, 15 * 256);
            endpointsSheet.setColumnWidth(3, 15 * 256);
            endpointsSheet.setColumnWidth(4, 15 * 256);
            endpointsSheet.setColumnWidth(5, 15 * 256);
        }

        return this;
    }

    public void startDetailedSheet() {
        detailsSheet = workbook.createSheet("Detailed Logs");
        detailsRowIdx = 0;

        String[] headers = {"ID", "Endpoint ID", "Endpoint Name", "Status", "Response Time (ms)", "Request", "Response", "Timestamp"};
        Row headerRow = detailsSheet.createRow(detailsRowIdx++);

        for (int i = 0; i < headers.length; i++) {
            createCell(headerRow, i, headers[i], headerStyle);
            maxColumnWidths[i] = headers[i].length() + 2;
        }

        detailsSheet.setColumnWidth(5, 80 * 256);
        detailsSheet.setColumnWidth(6, 80 * 256);
    }

    public void appendDetailRow(RequestLog logDto) {
        if (detailsSheet == null) {
            startDetailedSheet();
        }

        Row dataRow = detailsSheet.createRow(detailsRowIdx++);

        // Col 0: ID
        createCell(dataRow, 0, logDto.getId(), standardStyle);
        trackWidth(0, String.valueOf(logDto.getId()));

        // Col 1: Endpoint ID
        createCell(dataRow, 1, logDto.getEndpointId(), standardStyle);
        trackWidth(1, String.valueOf(logDto.getEndpointId()));

        // Col 2: Endpoint Name
        String epName = logDto.getEndpointName() != null ? logDto.getEndpointName() : "";
        createCell(dataRow, 2, epName, standardStyle);
        trackWidth(2, epName);

        // Col 3: Status
        createCell(dataRow, 3, logDto.getStatusCode(), standardStyle);
        trackWidth(3, String.valueOf(logDto.getStatusCode()));

        // Col 4: Response Time
        createCell(dataRow, 4, logDto.getResponseTime(), numberStyle);
        trackWidth(4, String.valueOf(logDto.getResponseTime()));

        // Col 5: Request (FIXED WIDTH, WRAPPED) -> NO TRACKING
        createCell(dataRow, 5, truncateText(logDto.getRequest()), wrapStyle);

        // Col 6: Response (FIXED WIDTH, WRAPPED) -> NO TRACKING
        createCell(dataRow, 6, truncateText(logDto.getResponse()), wrapStyle);

        // Col 7: Timestamp
        if (logDto.getCreatedAt() != null) {
            String ts = logDto.getCreatedAt().format(DATE_FORMATTER);
            createCell(dataRow, 7, ts, standardStyle);
            trackWidth(7, ts);
        } else {
            createCell(dataRow, 7, "", standardStyle);
        }
    }

    private void trackWidth(int colIndex, String text) {
        if (text == null) return;
        int length = text.length() + 2;
        if (length > maxColumnWidths[colIndex]) {
            maxColumnWidths[colIndex] = Math.min(length, 255);
        }
    }

    private String truncateText(String text) {
        if (text == null) return "";
        return text.length() > 32767 ? text.substring(0, 32767) : text;
    }

    public void export(HttpServletResponse response) throws IOException {
        log.info("Finalizing Excel layout and exporting...");

        if (detailsSheet != null) {
            detailsSheet.setColumnWidth(0, maxColumnWidths[0] * 256);
            detailsSheet.setColumnWidth(1, maxColumnWidths[1] * 256);
            detailsSheet.setColumnWidth(2, maxColumnWidths[2] * 256);
            detailsSheet.setColumnWidth(3, maxColumnWidths[3] * 256);
            detailsSheet.setColumnWidth(4, maxColumnWidths[4] * 256);
            detailsSheet.setColumnWidth(7, maxColumnWidths[7] * 256);
        }

        try (ServletOutputStream outputStream = response.getOutputStream()) {
            workbook.write(outputStream);
            log.info("Workbook written to output stream successfully.");
        } finally {
            workbook.close();
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
        if (style != null) cell.setCellStyle(style);
    }

    private CellStyle createHeaderStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createBodyStyle(boolean wrapText) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(wrapText);
        if (wrapText) {
            style.setVerticalAlignment(VerticalAlignment.TOP);
        }
        return style;
    }

    private void addRow(Sheet sheet, int rowNum, String label, Object value, CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowNum);
        Cell cell0 = row.createCell(0);
        cell0.setCellValue(label);
        cell0.setCellStyle(labelStyle);
        createCell(row, 1, value, valueStyle);
    }
}