package dev.zeann3th.stresspilot.core.utils;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFLineChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTLineSer;
import org.openxmlformats.schemas.drawingml.x2006.chart.STMarkerStyle;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
    private final TreeMap<LocalDateTime, TimeBucket> overallTimeBuckets = new TreeMap<>();
    private final Map<Long, TreeMap<LocalDateTime, TimeBucket>> endpointTimeBuckets = new LinkedHashMap<>();
    private final Map<Long, String> endpointNamesById = new LinkedHashMap<>();

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
        summary.setDisplayGridlines(false);
        int r = 0;
        addRow(summary, r++, "Run ID", report.getRunId(), headerStyle, standardStyle);
        addRow(summary, r++, "Total Requests", report.getTotalRequests(), headerStyle, intStyle);
        addRow(summary, r++, "Success Count", report.getSuccessCount(), headerStyle, intStyle);
        addRow(summary, r++, "Failure Count", report.getFailureCount(), headerStyle, intStyle);
        addRow(summary, r++, "Success Rate (%)", report.getSuccessRate(), headerStyle, numberStyle);
        addRow(summary, r++, "Average Response Time (ms)", report.getAvgResponse(), headerStyle, numberStyle);
        addRow(summary, r++, "P90 Response (ms)", report.getP90(), headerStyle, numberStyle);
        addRow(summary, r++, "P95 Response (ms)", report.getP95(), headerStyle, numberStyle);
        addRow(summary, r++, "TPS", report.getTps(), headerStyle, numberStyle);

        summary.setColumnWidth(0, 30 * 256);
        summary.setColumnWidth(1, 25 * 256);

        // --- ENDPOINTS SHEET ---
        if (report.getEndpointStats() != null && !report.getEndpointStats().isEmpty()) {
            SXSSFSheet endpointsSheet = workbook.createSheet("Endpoint Aggregates");
            endpointsSheet.setDisplayGridlines(false);
            Row head = endpointsSheet.createRow(0);
            String[] eHeaders = {"Endpoint ID", "Endpoint Name", "Requests", "Avg (ms)", "P90 (ms)", "P95 (ms)", "TPS"};
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
                createCell(rr, 6, calculateEndpointTps(stat, report.getDurationSeconds()), numberStyle);
            }

            endpointsSheet.setColumnWidth(0, 15 * 256);
            endpointsSheet.setColumnWidth(1, 40 * 256);
            endpointsSheet.setColumnWidth(2, 15 * 256);
            endpointsSheet.setColumnWidth(3, 15 * 256);
            endpointsSheet.setColumnWidth(4, 15 * 256);
            endpointsSheet.setColumnWidth(5, 15 * 256);
            endpointsSheet.setColumnWidth(6, 15 * 256);
            endpointsSheet.createFreezePane(0, 1);

            int lastEndpointRow = er - 1;
            endpointsSheet.setAutoFilter(new CellRangeAddress(0, lastEndpointRow, 0, 6));
        }

        return this;
    }

    public void startDetailedSheet() {
        detailsSheet = workbook.createSheet("Detailed Logs");
        detailsSheet.setDisplayGridlines(false);
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

        // Col 5: Request
        createCell(dataRow, 5, truncateText(logDto.getRequest()), wrapStyle);

        // Col 6: Response
        createCell(dataRow, 6, truncateText(logDto.getResponse()), wrapStyle);

        // Col 7: Timestamp
        if (logDto.getCreatedAt() != null) {
            String ts = logDto.getCreatedAt().format(DATE_FORMATTER);
            createCell(dataRow, 7, ts, standardStyle);
            trackWidth(7, ts);
        } else {
            createCell(dataRow, 7, "", standardStyle);
        }

        collectTimeSeriesPoint(logDto);
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
        addTimeSeriesCharts();

        try (ServletOutputStream outputStream = response.getOutputStream()) {
            writeTo(outputStream);
            log.info("Workbook written to output stream successfully.");
        } finally {
            workbook.close();
        }
    }

    public void writeTo(OutputStream outputStream) throws IOException {
        workbook.write(outputStream);
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

    private Double calculateEndpointTps(EndpointStats stat, Double durationSeconds) {
        if (stat.getRequests() == null || durationSeconds == null || durationSeconds <= 0) {
            return 0.0;
        }
        return stat.getRequests() / durationSeconds;
    }

    private void collectTimeSeriesPoint(RequestLog logDto) {
        LocalDateTime timestamp = logDto.getCreatedAt() != null ? logDto.getCreatedAt() : LocalDateTime.now();
        LocalDateTime bucketTime = timestamp.withNano(0);

        overallTimeBuckets.computeIfAbsent(bucketTime, ignored -> new TimeBucket())
            .add(logDto.getResponseTime());

        Long endpointId = logDto.getEndpointId();
        if (endpointId != null) {
            endpointNamesById.putIfAbsent(endpointId, endpointLabel(logDto));
            endpointTimeBuckets.computeIfAbsent(endpointId, ignored -> new TreeMap<>())
                .computeIfAbsent(bucketTime, ignored -> new TimeBucket())
                .add(logDto.getResponseTime());
        }
    }

    private String endpointLabel(RequestLog logDto) {
        if (logDto.getEndpointName() != null && !logDto.getEndpointName().isBlank()) {
            return logDto.getEndpointName();
        }
        return "Endpoint " + logDto.getEndpointId();
    }

    private void addTimeSeriesCharts() {
        if (overallTimeBuckets.isEmpty()) {
            return;
        }

        SXSSFSheet summary = workbook.getSheet("Summary");
        if (summary != null) {
            String[] labels = timeLabels(overallTimeBuckets);
            addLineChart(
                summary,
                "Overall Response Time",
                labels,
                Map.of("Avg ms", averageResponseSeries(overallTimeBuckets)),
                3,
                1,
                13,
                16
            );
            addLineChart(
                summary,
                "Overall TPS",
                labels,
                Map.of("TPS", tpsSeries(overallTimeBuckets)),
                3,
                18,
                13,
                33
            );
        }

        SXSSFSheet endpoints = workbook.getSheet("Endpoint Aggregates");
        if (endpoints != null && !endpointTimeBuckets.isEmpty()) {
            List<LocalDateTime> timeline = new ArrayList<>(overallTimeBuckets.keySet());
            String[] labels = timeline.stream()
                .map(time -> time.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .toArray(String[]::new);

            addLineChart(
                endpoints,
                "Response Time by Endpoint",
                labels,
                endpointSeries(timeline, true),
                8,
                1,
                20,
                18
            );
            addLineChart(
                endpoints,
                "TPS by Endpoint",
                labels,
                endpointSeries(timeline, false),
                8,
                20,
                20,
                37
            );
        }
    }

    private void addLineChart(
        SXSSFSheet sheet,
        String title,
        String[] labels,
        Map<String, Double[]> seriesValues,
        int chartCol1,
        int chartRow1,
        int chartCol2,
        int chartRow2
    ) {
        XSSFChart chart = createChart(sheet, title, chartCol1, chartRow1, chartCol2, chartRow2);
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setCrosses(org.apache.poi.xddf.usermodel.chart.AxisCrosses.AUTO_ZERO);

        XDDFCategoryDataSource categories = XDDFDataSourcesFactory.fromArray(labels);
        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);
        data.setVaryColors(false);

        for (Map.Entry<String, Double[]> entry : seriesValues.entrySet()) {
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromArray(entry.getValue());
            XDDFChartData.Series series = data.addSeries(categories, values);
            series.setTitle(entry.getKey());
        }

        chart.plot(data);
        smoothAndHideMarkers(chart);
    }

    private void smoothAndHideMarkers(XSSFChart chart) {
        chart.getCTChart().getPlotArea().getLineChartList().forEach(lineChart ->
            lineChart.getSerList().forEach(this::smoothAndHideMarker)
        );
    }

    private void smoothAndHideMarker(CTLineSer series) {
        if (!series.isSetMarker()) {
            series.addNewMarker();
        }
        if (!series.getMarker().isSetSymbol()) {
            series.getMarker().addNewSymbol();
        }
        series.getMarker().getSymbol().setVal(STMarkerStyle.NONE);

        if (!series.isSetSmooth()) {
            series.addNewSmooth();
        }
        series.getSmooth().setVal(true);
    }

    private String[] timeLabels(TreeMap<LocalDateTime, TimeBucket> buckets) {
        return buckets.keySet().stream()
            .map(time -> time.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
            .toArray(String[]::new);
    }

    private Double[] averageResponseSeries(TreeMap<LocalDateTime, TimeBucket> buckets) {
        return buckets.values().stream()
            .map(TimeBucket::averageResponse)
            .toArray(Double[]::new);
    }

    private Double[] tpsSeries(TreeMap<LocalDateTime, TimeBucket> buckets) {
        return buckets.values().stream()
            .map(bucket -> (double) bucket.requestCount)
            .toArray(Double[]::new);
    }

    private Map<String, Double[]> endpointSeries(List<LocalDateTime> timeline, boolean responseTime) {
        Map<String, Double[]> series = new LinkedHashMap<>();
        for (Map.Entry<Long, TreeMap<LocalDateTime, TimeBucket>> endpointEntry : endpointTimeBuckets.entrySet()) {
            Double[] values = new Double[timeline.size()];
            TreeMap<LocalDateTime, TimeBucket> buckets = endpointEntry.getValue();
            for (int i = 0; i < timeline.size(); i++) {
                TimeBucket bucket = buckets.get(timeline.get(i));
                values[i] = bucket == null ? 0.0 : responseTime ? bucket.averageResponse() : (double) bucket.requestCount;
            }
            series.put(endpointNamesById.getOrDefault(endpointEntry.getKey(), "Endpoint " + endpointEntry.getKey()), values);
        }
        return series;
    }

    private XSSFChart createChart(SXSSFSheet sheet, String title, int col1, int row1, int col2, int row2) {
        sheet.createDrawingPatriarch();
        XSSFDrawing drawing = sheet.getDrawingPatriarch();
        XSSFChart chart = drawing.createChart(drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2));
        chart.setTitleText(title);
        chart.setTitleOverlay(false);
        chart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);
        return chart;
    }

    private static class TimeBucket {
        private long requestCount;
        private long responseCount;
        private double responseTimeTotal;

        private void add(Long responseTime) {
            requestCount++;
            if (responseTime != null) {
                responseCount++;
                responseTimeTotal += responseTime;
            }
        }

        private double averageResponse() {
            return responseCount == 0 ? 0.0 : responseTimeTotal / responseCount;
        }
    }
}
