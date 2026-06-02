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
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTLineSer;
import org.openxmlformats.schemas.drawingml.x2006.chart.STMarkerStyle;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j(topic = "ExcelGenerator")
public class ExcelGenerator {

    private static final int WINDOW_SIZE = 1000;
    private static final String CHART_DATA_SHEET = "_Chart Data";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SXSSFWorkbook workbook;

    private final CellStyle headerStyle;
    private final CellStyle numberStyle;
    private final CellStyle intStyle;
    private final CellStyle wrapStyle;
    private final CellStyle standardStyle;
    private final CellStyle dashboardTitleStyle;
    private final CellStyle dashboardTileLabelStyle;
    private final CellStyle dashboardTileValueStyle;

    private SXSSFSheet detailsSheet;
    private SXSSFSheet chartDataSheet;
    private int detailsRowIdx = 0;
    private int chartDataRowIdx = 0;
    private RunReport report;

    private final int[] maxColumnWidths = new int[9];
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
        this.dashboardTitleStyle = createDashboardTitleStyle();
        this.dashboardTileLabelStyle = createDashboardTileLabelStyle();
        this.dashboardTileValueStyle = createDashboardTileValueStyle();
    }

    public ExcelGenerator writeSummarySheets(RunReport report) {
        this.report = report;
        log.info("Starting ultra-fast Excel generation for Run ID: {}", report.getRunId());

        // --- SUMMARY SHEET ---
        SXSSFSheet summary = workbook.createSheet("Summary");
        summary.setDisplayGridlines(false);
        int r = 0;
        addRow(summary, r++, "Run ID", report.getRunId(), headerStyle, standardStyle);
        addRow(summary, r++, "Total Requests", report.getTotalRequests(), headerStyle, intStyle);
        addRow(summary, r++, "Success Count", report.getSuccessCount(), headerStyle, intStyle);
        addRow(summary, r++, "Failure Count", report.getFailureCount(), headerStyle, intStyle);
        addRow(summary, r++, "Pass Rate (%)", report.getSuccessRate(), headerStyle, numberStyle);
        addRow(summary, r++, "Fail Rate (%)", report.getFailureRate(), headerStyle, numberStyle);
        addRow(summary, r++, "Average Response Time (ms)", report.getAvgResponse(), headerStyle, numberStyle);
        addRow(summary, r++, "P90 Response (ms)", report.getP90(), headerStyle, numberStyle);
        addRow(summary, r++, "P95 Response (ms)", report.getP95(), headerStyle, numberStyle);
        addRow(summary, r++, "RPS", report.getTps(), headerStyle, numberStyle);

        summary.setColumnWidth(0, 30 * 256);
        summary.setColumnWidth(1, 25 * 256);

        // --- ENDPOINTS SHEET ---
        if (report.getEndpointStats() != null && !report.getEndpointStats().isEmpty()) {
            SXSSFSheet endpointsSheet = workbook.createSheet("Endpoint Aggregates");
            endpointsSheet.setDisplayGridlines(false);
            Row head = endpointsSheet.createRow(0);
            String[] eHeaders = {"Endpoint ID", "Endpoint Name", "Requests", "Avg (ms)", "P90 (ms)", "P95 (ms)", "RPS"};
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
                createCell(rr, 6, calculateEndpointRps(stat, report.getDurationSeconds()), numberStyle);
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

        String[] headers = {"ID", "Correlation ID", "Endpoint ID", "Endpoint Name", "Status", "Response Time (ms)", "Request", "Response", "Timestamp"};
        Row headerRow = detailsSheet.createRow(detailsRowIdx++);

        for (int i = 0; i < headers.length; i++) {
            createCell(headerRow, i, headers[i], headerStyle);
            maxColumnWidths[i] = headers[i].length() + 2;
        }

        detailsSheet.setColumnWidth(6, 80 * 256);
        detailsSheet.setColumnWidth(7, 80 * 256);
    }

    public void appendDetailRow(RequestLog logDto) {
        if (detailsSheet == null) {
            startDetailedSheet();
        }

        Row dataRow = detailsSheet.createRow(detailsRowIdx++);

        // Col 0: ID
        createCell(dataRow, 0, logDto.getId(), standardStyle);
        trackWidth(0, String.valueOf(logDto.getId()));

        // Col 1: Correlation ID
        String correlationId = logDto.getCorrelationId() != null ? logDto.getCorrelationId() : "";
        createCell(dataRow, 1, correlationId, standardStyle);
        trackWidth(1, correlationId);

        // Col 2: Endpoint ID
        createCell(dataRow, 2, logDto.getEndpointId(), standardStyle);
        trackWidth(2, String.valueOf(logDto.getEndpointId()));

        // Col 3: Endpoint Name
        String epName = logDto.getEndpointName() != null ? logDto.getEndpointName() : "";
        createCell(dataRow, 3, epName, standardStyle);
        trackWidth(3, epName);

        // Col 4: Status
        createCell(dataRow, 4, logDto.getStatusCode(), standardStyle);
        trackWidth(4, String.valueOf(logDto.getStatusCode()));

        // Col 5: Response Time
        createCell(dataRow, 5, logDto.getResponseTime(), numberStyle);
        trackWidth(5, String.valueOf(logDto.getResponseTime()));

        // Col 6: Request
        createCell(dataRow, 6, truncateText(logDto.getRequest()), wrapStyle);

        // Col 7: Response
        createCell(dataRow, 7, truncateText(logDto.getResponse()), wrapStyle);

        // Col 8: Timestamp
        if (logDto.getCreatedAt() != null) {
            String ts = logDto.getCreatedAt().format(DATE_FORMATTER);
            createCell(dataRow, 8, ts, standardStyle);
            trackWidth(8, ts);
        } else {
            createCell(dataRow, 8, "", standardStyle);
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
            detailsSheet.setColumnWidth(5, maxColumnWidths[5] * 256);
            detailsSheet.setColumnWidth(8, maxColumnWidths[8] * 256);
        }
        addSummaryPieChart();
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

    private CellStyle createDashboardTitleStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 18);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle createDashboardTileLabelStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.GREY_80_PERCENT.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        return style;
    }

    private CellStyle createDashboardTileValueStyle() {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        font.setColor(IndexedColors.BLACK.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00"));
        return style;
    }

    private void addRow(Sheet sheet, int rowNum, String label, Object value, CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowNum);
        Cell cell0 = row.createCell(0);
        cell0.setCellValue(label);
        cell0.setCellStyle(labelStyle);
        createCell(row, 1, value, valueStyle);
    }

    private Double calculateEndpointRps(EndpointStats stat, Double durationSeconds) {
        if (stat.getRequests() == null || durationSeconds == null || durationSeconds <= 0) {
            return 0.0;
        }
        return stat.getRequests() / durationSeconds;
    }

    private void collectTimeSeriesPoint(RequestLog logDto) {
        LocalDateTime timestamp = logDto.getCreatedAt() != null ? logDto.getCreatedAt() : LocalDateTime.now();
        LocalDateTime bucketTime = timestamp.withNano(0);
        long payloadBytes = payloadBytes(logDto);

        TimeBucket overallBucket = overallTimeBuckets.computeIfAbsent(bucketTime, ignored -> new TimeBucket());
        overallBucket.add(logDto.getResponseTime(), logDto.getActiveThreads());
        overallBucket.addBytes(payloadBytes);

        Long endpointId = logDto.getEndpointId();
        if (endpointId != null) {
            endpointNamesById.putIfAbsent(endpointId, endpointLabel(logDto));
            TimeBucket endpointBucket = endpointTimeBuckets.computeIfAbsent(endpointId, ignored -> new TreeMap<>())
                .computeIfAbsent(bucketTime, ignored -> new TimeBucket());
            endpointBucket.add(logDto.getResponseTime(), logDto.getActiveThreads());
            endpointBucket.addBytes(payloadBytes);
        }
    }

    private long payloadBytes(RequestLog logDto) {
        return byteLength(logDto.getResponse());
    }

    private long byteLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
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

        SXSSFSheet charts = workbook.createSheet("Charts");
        charts.setDisplayGridlines(false);
        setupDashboardSheet(charts);
        addDashboardHeader(charts);
        addDashboardTiles(charts);

        String[] labels = timeLabels(overallTimeBuckets);
        addLineChart(
            charts,
            "Response Time Over Time",
            labels,
            Map.of("Avg ms", averageResponseSeries(overallTimeBuckets)),
            0,
            6,
            15,
            25
        );
        addLineChart(
            charts,
            "Response Time Percentiles Over Time",
            labels,
            percentileSeries(overallTimeBuckets),
            15,
            6,
            30,
            25
        );
        addLineChart(
            charts,
            "Requests Per Second Over Time",
            labels,
            Map.of("RPS", rpsSeries(overallTimeBuckets)),
            0,
            27,
            15,
            46
        );
        addLineChart(
            charts,
            "Active Threads Over Time",
            labels,
            Map.of("Active threads", activeThreadsSeries(overallTimeBuckets)),
            15,
            27,
            30,
            46
        );
        addLineChart(
            charts,
            "Response Bandwidth Over Time",
            labels,
            Map.of("Response KB/s", bandwidthSeries(overallTimeBuckets)),
            0,
            48,
            15,
            67
        );

        SXSSFSheet endpoints = workbook.getSheet("Endpoint Aggregates");
        if (endpoints != null && !endpointTimeBuckets.isEmpty()) {
            List<LocalDateTime> timeline = new ArrayList<>(overallTimeBuckets.keySet());
            String[] endpointLabels = timeline.stream()
                .map(time -> time.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .toArray(String[]::new);

            addLineChart(
                charts,
                "Response Time by Endpoint",
                endpointLabels,
                endpointSeries(timeline, true),
                15,
                48,
                30,
                67
            );
            addLineChart(
                charts,
                "RPS by Endpoint",
                endpointLabels,
                endpointSeries(timeline, false),
                0,
                69,
                30,
                88
            );
        }
    }

    private void setupDashboardSheet(SXSSFSheet charts) {
        for (int col = 0; col < 30; col++) {
            charts.setColumnWidth(col, 12 * 256);
        }
        for (int rowIndex = 0; rowIndex <= 88; rowIndex++) {
            charts.createRow(rowIndex).setHeightInPoints(rowIndex == 0 ? 26 : 20);
        }
    }

    private void addDashboardHeader(SXSSFSheet charts) {
        charts.addMergedRegion(new CellRangeAddress(0, 0, 0, 29));
        Row titleRow = charts.getRow(0);
        createCell(titleRow, 0, "StressPilot Run Dashboard", dashboardTitleStyle);
    }

    private void addDashboardTiles(SXSSFSheet charts) {
        addDashboardTile(charts, 2, 0, "Total Requests", report == null ? 0 : report.getTotalRequests());
        addDashboardTile(charts, 2, 5, "Pass Rate", report == null ? 0.0 : report.getSuccessRate());
        addDashboardTile(charts, 2, 10, "RPS", report == null ? 0.0 : report.getTps());
        addDashboardTile(charts, 2, 15, "Avg ms", report == null ? 0.0 : report.getAvgResponse());
        addDashboardTile(charts, 2, 20, "P95 ms", report == null ? 0.0 : report.getP95());
        addDashboardTile(charts, 2, 25, "Peak Resp KB/s", peakBandwidthKbps());
    }

    private void addDashboardTile(SXSSFSheet sheet, int rowIndex, int colIndex, String label, Object value) {
        sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, colIndex, colIndex + 3));
        sheet.addMergedRegion(new CellRangeAddress(rowIndex + 1, rowIndex + 3, colIndex, colIndex + 3));

        Row labelRow = sheet.getRow(rowIndex);
        Row valueRow = sheet.getRow(rowIndex + 1);
        createCell(labelRow, colIndex, label, dashboardTileLabelStyle);
        createCell(valueRow, colIndex, value, dashboardTileValueStyle);

        for (int r = rowIndex; r <= rowIndex + 3; r++) {
            Row row = sheet.getRow(r);
            for (int c = colIndex; c <= colIndex + 3; c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    cell = row.createCell(c);
                }
                cell.setCellStyle(r == rowIndex ? dashboardTileLabelStyle : dashboardTileValueStyle);
            }
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
        ChartRange range = writeChartData(labels, seriesValues);
        XSSFChart chart = createChart(sheet, title, chartCol1, chartRow1, chartCol2, chartRow2);
        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setCrosses(org.apache.poi.xddf.usermodel.chart.AxisCrosses.AUTO_ZERO);

        XDDFCategoryDataSource categories = XDDFDataSourcesFactory.fromStringCellRange(
            xssfChartDataSheet(),
            new CellRangeAddress(range.firstDataRow(), range.lastDataRow(), range.labelColumn(), range.labelColumn())
        );
        XDDFLineChartData data = (XDDFLineChartData) chart.createData(ChartTypes.LINE, bottomAxis, leftAxis);
        data.setVaryColors(false);

        int seriesIndex = 0;
        for (Map.Entry<String, Double[]> entry : seriesValues.entrySet()) {
            int valueColumn = range.firstValueColumn() + seriesIndex++;
            XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                xssfChartDataSheet(),
                new CellRangeAddress(range.firstDataRow(), range.lastDataRow(), valueColumn, valueColumn)
            );
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

    private Map<String, Double[]> percentileSeries(TreeMap<LocalDateTime, TimeBucket> buckets) {
        Map<String, Double[]> series = new LinkedHashMap<>();
        series.put("P50 ms", percentileResponseSeries(buckets, 50));
        series.put("P90 ms", percentileResponseSeries(buckets, 90));
        series.put("P95 ms", percentileResponseSeries(buckets, 95));
        return series;
    }

    private Double[] percentileResponseSeries(TreeMap<LocalDateTime, TimeBucket> buckets, int percentile) {
        return buckets.values().stream()
            .map(bucket -> bucket.percentileResponse(percentile))
            .toArray(Double[]::new);
    }

    private Double[] rpsSeries(TreeMap<LocalDateTime, TimeBucket> buckets) {
        return buckets.values().stream()
            .map(bucket -> (double) bucket.requestCount)
            .toArray(Double[]::new);
    }

    private Double[] activeThreadsSeries(TreeMap<LocalDateTime, TimeBucket> buckets) {
        return buckets.values().stream()
            .map(bucket -> (double) bucket.activeThreads)
            .toArray(Double[]::new);
    }

    private Double[] bandwidthSeries(TreeMap<LocalDateTime, TimeBucket> buckets) {
        return buckets.values().stream()
            .map(bucket -> bucket.bytes / 1024.0)
            .toArray(Double[]::new);
    }

    private double peakBandwidthKbps() {
        return overallTimeBuckets.values().stream()
            .mapToDouble(bucket -> bucket.bytes / 1024.0)
            .max()
            .orElse(0.0);
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

    private void addSummaryPieChart() {
        if (report == null) {
            return;
        }
        SXSSFSheet summary = workbook.getSheet("Summary");
        if (summary == null) {
            return;
        }

        int dataRow = 13;
        Row header = summary.createRow(dataRow);
        createCell(header, 3, "Result", headerStyle);
        createCell(header, 4, "Percentage", headerStyle);
        createCell(header, 5, "Count", headerStyle);

        Row pass = summary.createRow(dataRow + 1);
        createCell(pass, 3, "Pass", standardStyle);
        createCell(pass, 4, valueOrZero(report.getSuccessRate()), numberStyle);
        createCell(pass, 5, valueOrZero(report.getSuccessCount()), intStyle);

        Row fail = summary.createRow(dataRow + 2);
        createCell(fail, 3, "Fail", standardStyle);
        createCell(fail, 4, valueOrZero(report.getFailureRate()), numberStyle);
        createCell(fail, 5, valueOrZero(report.getFailureCount()), intStyle);

        XSSFChart chart = createChart(summary, "Request Summary", 3, 1, 11, 14);
        XDDFCategoryDataSource categories = XDDFDataSourcesFactory.fromStringCellRange(
            summary.getWorkbook().getXSSFWorkbook().getSheet(summary.getSheetName()),
            new CellRangeAddress(dataRow + 1, dataRow + 2, 3, 3)
        );
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
            summary.getWorkbook().getXSSFWorkbook().getSheet(summary.getSheetName()),
            new CellRangeAddress(dataRow + 1, dataRow + 2, 5, 5)
        );
        XDDFPieChartData data = (XDDFPieChartData) chart.createData(ChartTypes.PIE, null, null);
        data.addSeries(categories, values);
        chart.plot(data);
    }

    private double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private ChartRange writeChartData(String[] labels, Map<String, Double[]> seriesValues) {
        SXSSFSheet dataSheet = chartDataSheet();
        int headerRowIndex = chartDataRowIdx++;
        Row header = dataSheet.createRow(headerRowIndex);
        createCell(header, 0, "Time", headerStyle);

        int seriesCol = 1;
        for (String seriesName : seriesValues.keySet()) {
            createCell(header, seriesCol++, seriesName, headerStyle);
        }

        int firstDataRow = chartDataRowIdx;
        for (int i = 0; i < labels.length; i++) {
            Row row = dataSheet.createRow(chartDataRowIdx++);
            createCell(row, 0, labels[i], standardStyle);
            int valueCol = 1;
            for (Double[] values : seriesValues.values()) {
                createCell(row, valueCol++, i < values.length ? values[i] : 0.0, numberStyle);
            }
        }
        int lastDataRow = Math.max(firstDataRow, chartDataRowIdx - 1);
        chartDataRowIdx++;

        return new ChartRange(firstDataRow, lastDataRow, 0, 1);
    }

    private SXSSFSheet chartDataSheet() {
        if (chartDataSheet == null) {
            chartDataSheet = workbook.createSheet(CHART_DATA_SHEET);
            chartDataSheet.setDisplayGridlines(false);
            int index = workbook.getSheetIndex(CHART_DATA_SHEET);
            if (index >= 0) {
                workbook.setSheetHidden(index, true);
            }
        }
        return chartDataSheet;
    }

    private org.apache.poi.xssf.usermodel.XSSFSheet xssfChartDataSheet() {
        chartDataSheet();
        return workbook.getXSSFWorkbook().getSheet(CHART_DATA_SHEET);
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
        private int activeThreads;
        private long bytes;
        private final List<Long> responseTimes = new ArrayList<>();

        private void add(Long responseTime, Integer activeThreads) {
            requestCount++;
            if (responseTime != null) {
                responseCount++;
                responseTimeTotal += responseTime;
                responseTimes.add(responseTime);
            }
            if (activeThreads != null) {
                this.activeThreads = Math.max(this.activeThreads, activeThreads);
            }
        }

        private void addBytes(long bytes) {
            this.bytes += bytes;
        }

        private double averageResponse() {
            return responseCount == 0 ? 0.0 : responseTimeTotal / responseCount;
        }

        private double percentileResponse(int percentile) {
            if (responseTimes.isEmpty()) {
                return 0.0;
            }
            Collections.sort(responseTimes);
            int index = (int) Math.ceil(percentile / 100.0 * responseTimes.size()) - 1;
            return responseTimes.get(Math.clamp(index, 0, responseTimes.size() - 1));
        }
    }

    private record ChartRange(int firstDataRow, int lastDataRow, int labelColumn, int firstValueColumn) { }
}
