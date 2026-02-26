package dev.zeann3th.stresspilot.infrastructure.report;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "ExcelGenerator")
@Getter
@Setter
public class ExcelGenerator<T> {

    private static final String FONT_NAME = "Times New Roman";
    private static final int HEADER_FONT_SIZE = 14;
    private static final int BODY_FONT_SIZE = 12;
    private static final int WINDOW_SIZE = 100;

    private List<T> data;
    private final SXSSFWorkbook workbook;
    private SXSSFSheet sheet;
    private int totalColumns = 0;
    private final ObjectMapper mapper;

    public ExcelGenerator() {
        this(Collections.emptyList());
    }

    public ExcelGenerator(List<T> data) {
        this.data = data;
        this.workbook = new SXSSFWorkbook(WINDOW_SIZE);
        this.workbook.setCompressTempFiles(true);
        this.mapper = new ObjectMapper();
    }

    public ExcelGenerator<T> writeHeaderLines(String[] headers) {
        log.info("Writing generic headers: {}", (Object) headers);
        sheet = workbook.createSheet("Sheet1");
        Row row = sheet.createRow(0);
        CellStyle style = createHeaderStyle();
        int col = 0;
        createCell(row, col++, "No", style);
        for (String header : headers) {
            createCell(row, col++, header, style);
        }
        this.totalColumns = headers.length + 1;
        return this;
    }

    public ExcelGenerator<T> writeDataLines(String[] fields) {
        log.info("Writing generic data lines, count: {}", data.size());
        CellStyle style = createBodyStyle(false);
        int rowCount = 1;
        for (T obj : data) {
            Row row = sheet.createRow(rowCount);
            int colCount = 0;
            createCell(row, colCount++, rowCount, style);
            Map<String, Object> map = mapper.convertValue(obj, new TypeReference<>() {
            });
            for (String field : fields) {
                createCell(row, colCount++, map.get(field), style);
            }
            rowCount++;
            checkFlush(sheet);
        }
        return this;
    }

    public ExcelGenerator<T> writeRunReport(RunReport report) {
        if (report == null) {
            log.error("RunReport is NULL. Cannot generate report.");
            return this;
        }

        log.info("Starting Excel generation for Run ID: {}", report.getRunId());
        long startTime = System.currentTimeMillis();

        // 1. Prepare Styles
        CellStyle headerStyle = createHeaderStyle();
        CellStyle numberStyle = createBodyStyle(false);
        numberStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        CellStyle intStyle = createBodyStyle(false);
        intStyle.setDataFormat(workbook.createDataFormat().getFormat("0"));
        CellStyle wrapStyle = createBodyStyle(true);

        // ==========================================
        // SHEET 1: SUMMARY
        // ==========================================
        log.debug("Creating Summary sheet...");
        SXSSFSheet summary = workbook.createSheet("Summary");
        summary.trackAllColumnsForAutoSizing();

        int r = 0;
        addRow(summary, r++, "Run ID", report.getRunId(), headerStyle, null);
        addRow(summary, r++, "Total Requests", report.getTotalRequests(), headerStyle, intStyle);
        addRow(summary, r++, "Success Count", report.getSuccessCount(), headerStyle, intStyle);
        addRow(summary, r++, "Failure Count", report.getFailureCount(), headerStyle, intStyle);
        addRow(summary, r++, "Success Rate (%)", report.getSuccessRate(), headerStyle, numberStyle);
        addRow(summary, r++, "Failure Rate (%)", report.getFailureRate(), headerStyle, numberStyle);
        addRow(summary, r++, "Average Response Time (ms)", report.getAvgResponse(), headerStyle, numberStyle);
        addRow(summary, r++, "P90 Response Time (ms)", report.getP90(), headerStyle, numberStyle);
        addRow(summary, r++, "P95 Response Time (ms)", report.getP95(), headerStyle, numberStyle);
        addRow(summary, r++, "Duration (s)", report.getDurationSeconds(), headerStyle, numberStyle);
        addRow(summary, r++, "TPS (requests/sec)", report.getTps(), headerStyle, numberStyle);

        if (report.getCcus() != null)
            addRow(summary, r++, "CCUs", report.getCcus(), headerStyle, intStyle);
        if (report.getRampUpTime() != null)
            addRow(summary, r++, "Ramp Up Time (s)", report.getRampUpTime(), headerStyle, null);
        if (report.getConfiguredDuration() != null)
            addRow(summary, r++, "Configured Duration (s)", report.getConfiguredDuration(), headerStyle, null);

        // ==========================================
        // SHEET 2: ENDPOINT AGGREGATES
        // ==========================================
        log.debug("Creating Endpoint Aggregates sheet...");
        SXSSFSheet endpointsSheet = workbook.createSheet("Endpoint Aggregates");
        endpointsSheet.trackAllColumnsForAutoSizing();

        Row head = endpointsSheet.createRow(0);
        String[] eHeaders = { "Endpoint ID", "Requests", "Avg (ms)", "P90 (ms)", "P95 (ms)" };
        for (int i = 0; i < eHeaders.length; i++) {
            Cell ch = head.createCell(i);
            ch.setCellValue(eHeaders[i]);
            ch.setCellStyle(headerStyle);
        }

        int er = 1;
        if (report.getEndpointStats() != null) {
            log.info("Writing {} endpoint stats rows.", report.getEndpointStats().size());
            for (EndpointStats stat : report.getEndpointStats()) {
                if (stat == null)
                    continue;
                Row rr = endpointsSheet.createRow(er++);
                createCell(rr, 0, stat.getEndpointName() != null ? stat.getEndpointName() : "error", wrapStyle);
                createCell(rr, 1, stat.getRequests(), intStyle);
                createCell(rr, 2, stat.getAvgMs(), numberStyle);
                createCell(rr, 3, stat.getP90Ms(), numberStyle);
                createCell(rr, 4, stat.getP95Ms(), numberStyle);
            }
        } else {
            log.warn("Endpoint stats list is null.");
        }

        // ==========================================
        // SHEET 3: DETAILS
        // ==========================================
        log.debug("Creating Details sheet...");
        SXSSFSheet details = workbook.createSheet("Details");

        String[] headers = { "ID", "Endpoint ID", "Status", "Response Time (ms)", "Request", "Response", "Timestamp" };
        Row headerRow = details.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        if (report.getDetails() != null) {
            int totalDetails = report.getDetails().size();
            log.info("Writing {} detail rows.", totalDetails);

            for (RequestLog logDto : report.getDetails()) {
                if (logDto == null)
                    continue;

                Row dataRow = details.createRow(rowIdx++);

                createCell(dataRow, 0, logDto.getId(), null);
                createCell(dataRow, 1, logDto.getEndpointId(), null);
                createCell(dataRow, 2, logDto.getStatusCode(), null);
                createCell(dataRow, 3, logDto.getResponseTime(), numberStyle);
                createCell(dataRow, 4, logDto.getRequest(), wrapStyle);
                createCell(dataRow, 5, logDto.getResponse(), wrapStyle);

                LocalDateTime ts = logDto.getCreatedAt();
                if (ts != null) {
                    createCell(dataRow, 6, ts.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), null);
                } else {
                    createCell(dataRow, 6, "", null);
                }

                if (rowIdx % WINDOW_SIZE == 0) {
                    checkFlush(details);
                }

                // Log progress for large datasets
                if (rowIdx % 1000 == 0) {
                    log.debug("Written {}/{} detail rows...", rowIdx, totalDetails);
                }
            }
        } else {
            log.warn("Details list is null.");
        }

        // ==========================================
        // FINAL SIZING
        // ==========================================
        log.debug("Auto-sizing columns...");
        autoSizeSheet(summary, 2);
        autoSizeSheet(endpointsSheet, eHeaders.length);

        details.setColumnWidth(0, 15 * 256);
        details.setColumnWidth(1, 15 * 256);
        details.setColumnWidth(2, 12 * 256);
        details.setColumnWidth(3, 20 * 256);
        details.setColumnWidth(4, 80 * 256);
        details.setColumnWidth(5, 80 * 256);
        details.setColumnWidth(6, 25 * 256);

        log.info("Excel generation completed in {} ms", System.currentTimeMillis() - startTime);
        return this;
    }

    public void export(HttpServletResponse response) throws IOException {
        log.info("Starting stream export to response...");
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            workbook.write(outputStream);
            log.info("Workbook written to output stream successfully.");
        } catch (Exception e) {
            log.error("Failed to write workbook to output stream", e);
            throw e;
        } finally {
            workbook.close();
            log.debug("Workbook disposed.");
        }
    }

    private CellStyle createHeaderStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) HEADER_FONT_SIZE);
        font.setFontName(FONT_NAME);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createBodyStyle(boolean wrapText) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) BODY_FONT_SIZE);
        font.setFontName(FONT_NAME);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setWrapText(wrapText);
        if (wrapText) {
            style.setVerticalAlignment(VerticalAlignment.TOP);
        }
        return style;
    }

    private void addRow(Sheet sheet, int rowNum, String label, Object value, CellStyle labelStyle,
            CellStyle valueStyle) {
        Row row = sheet.createRow(rowNum);
        Cell cell0 = row.createCell(0);
        cell0.setCellValue(label);
        cell0.setCellStyle(labelStyle);
        createCell(row, 1, value, valueStyle);
    }

    private void createCell(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        try {
            switch (value) {
                case null -> cell.setCellValue("");
                case Number num -> {
                    long longVal = num.longValue();
                    if (isPossibleEpoch(longVal)) {
                        LocalDateTime ldt = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(longVal), ZoneId.systemDefault());
                        cell.setCellValue(ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    } else {
                        cell.setCellValue(num.doubleValue());
                    }
                }
                case Boolean bool -> cell.setCellValue(bool);
                case Date date -> cell.setCellValue(date);
                case LocalDateTime ldt -> cell.setCellValue(ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                default -> {
                    String text = value.toString();
                    if (text.length() > 32767) {
                        text = text.substring(0, 32767);
                    }
                    cell.setCellValue(text);
                }
            }
            if (style != null) {
                cell.setCellStyle(style);
            }
        } catch (Exception e) {
            log.error("Error creating cell at col {} with value: {}", columnIndex, value, e);
        }
    }

    private boolean isPossibleEpoch(long value) {
        return value >= 946684800000L && value <= 4102444800000L;
    }

    private void checkFlush(SXSSFSheet s) {
        try {
            s.flushRows(WINDOW_SIZE);
        } catch (IOException e) {
            log.error("Error flushing rows", e);
        }
    }

    private void autoSizeSheet(SXSSFSheet s, int cols) {
        try {
            for (int i = 0; i < cols; i++) {
                s.autoSizeColumn(i);
                s.setColumnWidth(i, Math.min(s.getColumnWidth(i) + 512, 255 * 256));
            }
        } catch (Exception e) {
            log.warn("Failed to auto-size sheet: {}", s.getSheetName(), e);
        }
    }
}
