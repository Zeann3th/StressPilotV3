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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RunComparisonExcelGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CHART_DATA_SHEET = "_Comparison Chart Data";
    private static final int WINDOW_SIZE = 1000;

    private final SXSSFWorkbook workbook;
    private final CellStyle headerStyle;
    private final CellStyle standardStyle;
    private final CellStyle numberStyle;
    private final CellStyle wrapStyle;
    private final RunReport left;
    private final RunReport right;
    private SXSSFSheet detailSheet;
    private SXSSFSheet chartDataSheet;
    private int detailRowIdx;
    private int chartDataRowIdx;
    private boolean writingLeftDetail = true;
    private final TreeMap<LocalDateTime, TimeBucket> leftOverallTimeBuckets = new TreeMap<>();
    private final TreeMap<LocalDateTime, TimeBucket> rightOverallTimeBuckets = new TreeMap<>();
    private final Map<Long, TreeMap<LocalDateTime, TimeBucket>> leftEndpointTimeBuckets = new LinkedHashMap<>();
    private final Map<Long, TreeMap<LocalDateTime, TimeBucket>> rightEndpointTimeBuckets = new LinkedHashMap<>();
    private final Map<Long, String> endpointNamesById = new LinkedHashMap<>();

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
        writingLeftDetail = sheetName == null || !sheetName.toLowerCase().contains("run b");
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
        collectTimeSeriesPoint(log);
    }

    public void export(HttpServletResponse response) throws IOException {
        addTimeSeriesCharts();
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

    private void collectTimeSeriesPoint(RequestLog log) {
        LocalDateTime timestamp = log.getCreatedAt() != null ? log.getCreatedAt() : LocalDateTime.now();
        LocalDateTime bucketTime = timestamp.withNano(0);
        long payloadBytes = byteLength(log.getResponse());

        TreeMap<LocalDateTime, TimeBucket> overallBuckets = writingLeftDetail
                ? leftOverallTimeBuckets
                : rightOverallTimeBuckets;
        TimeBucket overallBucket = overallBuckets.computeIfAbsent(bucketTime, ignored -> new TimeBucket());
        overallBucket.add(log.getResponseTime(), log.getActiveThreads());
        overallBucket.addBytes(payloadBytes);

        Long endpointId = log.getEndpointId();
        if (endpointId != null) {
            endpointNamesById.putIfAbsent(endpointId, endpointLabel(log));
            Map<Long, TreeMap<LocalDateTime, TimeBucket>> endpointBuckets = writingLeftDetail
                    ? leftEndpointTimeBuckets
                    : rightEndpointTimeBuckets;
            TimeBucket endpointBucket = endpointBuckets.computeIfAbsent(endpointId, ignored -> new TreeMap<>())
                    .computeIfAbsent(bucketTime, ignored -> new TimeBucket());
            endpointBucket.add(log.getResponseTime(), log.getActiveThreads());
            endpointBucket.addBytes(payloadBytes);
        }
    }

    private void addTimeSeriesCharts() {
        if (leftOverallTimeBuckets.isEmpty() && rightOverallTimeBuckets.isEmpty()) {
            return;
        }

        SXSSFSheet charts = workbook.createSheet("Charts");
        charts.setDisplayGridlines(false);
        setupDashboardSheet(charts);
        addDashboardHeader(charts);
        addDashboardTiles(charts);

        List<LocalDateTime> timeline = mergedTimeline(leftOverallTimeBuckets, rightOverallTimeBuckets);
        String[] labels = timeLabels(timeline);
        addLineChart(
                charts,
                "Response Time Over Time",
                labels,
                twoRunSeries(timeline, "Avg ms", leftOverallTimeBuckets, rightOverallTimeBuckets,
                        TimeBucket::averageResponse),
                0,
                6,
                15,
                25
        );
        addLineChart(
                charts,
                "Response Time Percentiles Over Time",
                labels,
                percentileSeries(timeline),
                15,
                6,
                30,
                25
        );
        addLineChart(
                charts,
                "Requests Per Second Over Time",
                labels,
                twoRunSeries(timeline, "RPS", leftOverallTimeBuckets, rightOverallTimeBuckets,
                        bucket -> (double) bucket.requestCount),
                0,
                27,
                15,
                46
        );
        addLineChart(
                charts,
                "Active Threads Over Time",
                labels,
                twoRunSeries(timeline, "Active threads", leftOverallTimeBuckets, rightOverallTimeBuckets,
                        bucket -> (double) bucket.activeThreads),
                15,
                27,
                30,
                46
        );
        addLineChart(
                charts,
                "Response Bandwidth Over Time",
                labels,
                twoRunSeries(timeline, "Response KB/s", leftOverallTimeBuckets, rightOverallTimeBuckets,
                        bucket -> bucket.bytes / 1024.0),
                0,
                48,
                15,
                67
        );

        if (!leftEndpointTimeBuckets.isEmpty() || !rightEndpointTimeBuckets.isEmpty()) {
            addLineChart(
                    charts,
                    "Response Time by Endpoint",
                    labels,
                    endpointSeries(timeline, true),
                    15,
                    48,
                    30,
                    67
            );
            addLineChart(
                    charts,
                    "RPS by Endpoint",
                    labels,
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
        createCell(titleRow, 0, "StressPilot Comparison Dashboard", headerStyle);
    }

    private void addDashboardTiles(SXSSFSheet charts) {
        createCell(charts.getRow(2), 0, "Run A", headerStyle);
        createCell(charts.getRow(3), 0, left.getRunId(), standardStyle);
        createCell(charts.getRow(2), 5, "Run B", headerStyle);
        createCell(charts.getRow(3), 5, right.getRunId(), standardStyle);
        createCell(charts.getRow(2), 10, "A Total", headerStyle);
        createCell(charts.getRow(3), 10, left.getTotalRequests(), numberStyle);
        createCell(charts.getRow(2), 15, "B Total", headerStyle);
        createCell(charts.getRow(3), 15, right.getTotalRequests(), numberStyle);
        createCell(charts.getRow(2), 20, "A P95", headerStyle);
        createCell(charts.getRow(3), 20, left.getP95(), numberStyle);
        createCell(charts.getRow(2), 25, "B P95", headerStyle);
        createCell(charts.getRow(3), 25, right.getP95(), numberStyle);
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
        smoothHideMarkersAndColor(chart);
    }

    private void smoothHideMarkersAndColor(XSSFChart chart) {
        chart.getCTChart().getPlotArea().getLineChartList().forEach(lineChart -> {
            int index = 0;
            for (CTLineSer series : lineChart.getSerList()) {
                smoothAndHideMarker(series);
                colorSeries(series, index++);
            }
        });
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

    private void colorSeries(CTLineSer series, int index) {
        byte[][] colors = {
                new byte[]{0x38, (byte) 0xBD, (byte) 0xF8},
                new byte[]{(byte) 0xF9, 0x73, 0x16},
                new byte[]{0x14, (byte) 0xB8, (byte) 0xA6},
                new byte[]{(byte) 0xA8, 0x55, (byte) 0xF7},
                new byte[]{0x22, (byte) 0xC5, 0x5E},
                new byte[]{(byte) 0xEF, 0x44, 0x44}
        };
        if (!series.isSetSpPr()) {
            series.addNewSpPr();
        }
        if (!series.getSpPr().isSetLn()) {
            series.getSpPr().addNewLn();
        }
        if (!series.getSpPr().getLn().isSetSolidFill()) {
            series.getSpPr().getLn().addNewSolidFill();
        }
        if (!series.getSpPr().getLn().getSolidFill().isSetSrgbClr()) {
            series.getSpPr().getLn().getSolidFill().addNewSrgbClr();
        }
        series.getSpPr().getLn().getSolidFill().getSrgbClr().setVal(colors[index % colors.length]);
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

    private List<LocalDateTime> mergedTimeline(TreeMap<LocalDateTime, TimeBucket> leftBuckets,
            TreeMap<LocalDateTime, TimeBucket> rightBuckets) {
        TreeSet<LocalDateTime> timeline = new TreeSet<>();
        timeline.addAll(leftBuckets.keySet());
        timeline.addAll(rightBuckets.keySet());
        return new ArrayList<>(timeline);
    }

    private String[] timeLabels(List<LocalDateTime> timeline) {
        return timeline.stream()
                .map(time -> time.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
                .toArray(String[]::new);
    }

    private Map<String, Double[]> twoRunSeries(List<LocalDateTime> timeline,
            String metricName,
            TreeMap<LocalDateTime, TimeBucket> leftBuckets,
            TreeMap<LocalDateTime, TimeBucket> rightBuckets,
            BucketValue valueProvider) {
        Map<String, Double[]> series = new LinkedHashMap<>();
        series.put(left.getRunId() + " - " + metricName, values(timeline, leftBuckets, valueProvider));
        series.put(right.getRunId() + " - " + metricName, values(timeline, rightBuckets, valueProvider));
        return series;
    }

    private Map<String, Double[]> percentileSeries(List<LocalDateTime> timeline) {
        Map<String, Double[]> series = new LinkedHashMap<>();
        series.put(left.getRunId() + " - P50 ms", values(timeline, leftOverallTimeBuckets, bucket -> bucket.percentileResponse(50)));
        series.put(right.getRunId() + " - P50 ms", values(timeline, rightOverallTimeBuckets, bucket -> bucket.percentileResponse(50)));
        series.put(left.getRunId() + " - P90 ms", values(timeline, leftOverallTimeBuckets, bucket -> bucket.percentileResponse(90)));
        series.put(right.getRunId() + " - P90 ms", values(timeline, rightOverallTimeBuckets, bucket -> bucket.percentileResponse(90)));
        series.put(left.getRunId() + " - P95 ms", values(timeline, leftOverallTimeBuckets, bucket -> bucket.percentileResponse(95)));
        series.put(right.getRunId() + " - P95 ms", values(timeline, rightOverallTimeBuckets, bucket -> bucket.percentileResponse(95)));
        return series;
    }

    private Double[] values(List<LocalDateTime> timeline,
            TreeMap<LocalDateTime, TimeBucket> buckets,
            BucketValue valueProvider) {
        Double[] values = new Double[timeline.size()];
        for (int i = 0; i < timeline.size(); i++) {
            TimeBucket bucket = buckets.get(timeline.get(i));
            values[i] = bucket == null ? 0.0 : valueProvider.value(bucket);
        }
        return values;
    }

    private Map<String, Double[]> endpointSeries(List<LocalDateTime> timeline, boolean responseTime) {
        Map<String, Double[]> series = new LinkedHashMap<>();
        TreeSet<Long> endpointIds = new TreeSet<>();
        endpointIds.addAll(leftEndpointTimeBuckets.keySet());
        endpointIds.addAll(rightEndpointTimeBuckets.keySet());
        for (Long endpointId : endpointIds) {
            String endpointName = endpointNamesById.getOrDefault(endpointId, "Endpoint " + endpointId);
            BucketValue valueProvider = responseTime
                    ? TimeBucket::averageResponse
                    : bucket -> (double) bucket.requestCount;
            series.put(left.getRunId() + " - " + endpointName, values(timeline,
                    leftEndpointTimeBuckets.getOrDefault(endpointId, new TreeMap<>()), valueProvider));
            series.put(right.getRunId() + " - " + endpointName, values(timeline,
                    rightEndpointTimeBuckets.getOrDefault(endpointId, new TreeMap<>()), valueProvider));
        }
        return series;
    }

    private String endpointLabel(RequestLog log) {
        if (log.getEndpointName() != null && !log.getEndpointName().isBlank()) {
            return log.getEndpointName();
        }
        return "Endpoint " + log.getEndpointId();
    }

    private long byteLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    @FunctionalInterface
    private interface BucketValue {
        double value(TimeBucket bucket);
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
