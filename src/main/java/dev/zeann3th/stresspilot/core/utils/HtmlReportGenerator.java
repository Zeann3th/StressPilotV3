package dev.zeann3th.stresspilot.core.utils;

import dev.zeann3th.stresspilot.core.domain.commands.run.EndpointStats;
import dev.zeann3th.stresspilot.core.domain.commands.run.RequestLog;
import dev.zeann3th.stresspilot.core.domain.commands.run.RunReport;

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

public class HtmlReportGenerator {
    private static final int SAMPLE_LOG_LIMIT = 300;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String[] COLORS = {"#2563eb", "#dc2626", "#65a30d", "#7c3aed", "#ea580c", "#0891b2", "#be123c"};

    private final RunReport report;
    private final TreeMap<LocalDateTime, TimeBucket> overallTimeBuckets = new TreeMap<>();
    private final Map<Long, TreeMap<LocalDateTime, TimeBucket>> endpointTimeBuckets = new LinkedHashMap<>();
    private final Map<Long, String> endpointNamesById = new LinkedHashMap<>();
    private final List<RequestLog> sampledLogs = new ArrayList<>();

    private int expectedLogCount;
    private int seenLogCount;
    private int sampleSlot;
    private int nextSampleIndex;

    public HtmlReportGenerator(RunReport report) {
        this.report = report;
    }

    public void startDetailedLogs(int expectedLogCount) {
        this.expectedLogCount = Math.max(expectedLogCount, 0);
        this.sampleSlot = 0;
        this.nextSampleIndex = 0;
    }

    public void appendLog(RequestLog log) {
        collectTimeSeriesPoint(log);
        sampleLog(log);
        seenLogCount++;
    }

    public void writeTo(OutputStream outputStream) throws IOException {
        outputStream.write(render().getBytes(StandardCharsets.UTF_8));
    }

    private void sampleLog(RequestLog log) {
        if (expectedLogCount <= SAMPLE_LOG_LIMIT) {
            if (sampledLogs.size() < SAMPLE_LOG_LIMIT) {
                sampledLogs.add(log);
            }
            return;
        }

        if (sampledLogs.size() < SAMPLE_LOG_LIMIT && seenLogCount >= nextSampleIndex) {
            sampledLogs.add(log);
            sampleSlot++;
            nextSampleIndex = (int) Math.round(sampleSlot * (expectedLogCount - 1.0) / (SAMPLE_LOG_LIMIT - 1.0));
        }
    }

    private void collectTimeSeriesPoint(RequestLog logDto) {
        LocalDateTime timestamp = logDto.getCreatedAt() != null ? logDto.getCreatedAt() : LocalDateTime.now();
        LocalDateTime bucketTime = timestamp.withNano(0);
        long payloadBytes = byteLength(logDto.getResponse());

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

    private String render() {
        StringBuilder html = new StringBuilder(200_000);
        html.append("""
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <title>StressPilot Run Report</title>
              <style>
                body{font-family:Arial,sans-serif;margin:24px;color:#111827;background:#fff}
                h1{font-size:22px;margin:0 0 16px} h2{font-size:16px;margin:26px 0 10px;color:#374151}
                .grid{display:grid;grid-template-columns:repeat(6,1fr);gap:10px;margin-bottom:18px}
                .card{border:1px solid #d1d5db;border-radius:6px;padding:10px;background:#f9fafb}
                .label{font-size:11px;color:#6b7280;font-weight:700}.value{font-size:18px;font-weight:700;margin-top:6px}
                .charts{display:grid;grid-template-columns:1fr 1fr;gap:16px}.chart{border:1px solid #d1d5db;border-radius:6px;padding:12px}
                .summary{display:grid;grid-template-columns:260px 1fr;gap:16px;align-items:start}
                .pie{width:210px;height:210px;border-radius:50%;border:1px solid #d1d5db}
                table{border-collapse:collapse;width:100%;font-size:12px}th{background:#dbeafe;text-align:left}td,th{border:1px solid #d1d5db;padding:6px;vertical-align:top}
                pre{white-space:pre-wrap;max-height:120px;overflow:auto;margin:0}.muted{color:#6b7280}
                @media(max-width:900px){.grid,.charts,.summary{grid-template-columns:1fr}}
              </style>
            </head>
            <body>
            """);
        html.append("<h1>StressPilot Run Dashboard</h1>");
        appendCards(html);
        html.append("<div class=\"charts\">");
        appendOverallCharts(html);
        html.append("</div>");
        appendSummaryTable(html);
        appendEndpointTable(html);
        appendLogTable(html);
        html.append("</body></html>");
        return html.toString();
    }

    private void appendCards(StringBuilder html) {
        html.append("<div class=\"grid\">");
        card(html, "Total Requests", report.getTotalRequests());
        card(html, "Pass Rate", percent(report.getSuccessRate()));
        card(html, "RPS", report.getTps());
        card(html, "Avg ms", report.getAvgResponse());
        card(html, "P95 ms", report.getP95());
        card(html, "Peak Resp KB/s", peakBandwidthKbps());
        html.append("</div>");
    }

    private void appendOverallCharts(StringBuilder html) {
        String[] labels = timeLabels(overallTimeBuckets);
        chart(html, "Response Time Over Time", labels, Map.of("Avg ms", averageResponseSeries(overallTimeBuckets)));
        chart(html, "Response Time Percentiles Over Time", labels, percentileSeries(overallTimeBuckets));
        chart(html, "Requests Per Second Over Time", labels, Map.of("RPS", rpsSeries(overallTimeBuckets)));
        chart(html, "Active Threads Over Time", labels, Map.of("Active threads", activeThreadsSeries(overallTimeBuckets)));
        chart(html, "Response Bandwidth Over Time", labels, Map.of("Response KB/s", bandwidthSeries(overallTimeBuckets)));

        if (!endpointTimeBuckets.isEmpty()) {
            List<LocalDateTime> timeline = new ArrayList<>(overallTimeBuckets.keySet());
            String[] endpointLabels = timeline.stream().map(time -> time.format(TIME_FORMATTER)).toArray(String[]::new);
            chart(html, "Response Time by Endpoint", endpointLabels, endpointSeries(timeline, true));
            chart(html, "RPS by Endpoint", endpointLabels, endpointSeries(timeline, false));
        }
    }

    private void appendSummaryTable(StringBuilder html) {
        double pass = valueOrZero(report.getSuccessRate());
        html.append("<h2>Request Summary</h2><div class=\"summary\"><div class=\"pie\" style=\"background:conic-gradient(#16a34a 0 ")
            .append(round(pass)).append("%,#dc2626 ").append(round(pass)).append("% 100%)\"></div>");
        html.append("<table><tr><th>Result</th><th>Percentage</th><th>Count</th></tr>");
        html.append("<tr><td>Pass</td><td>").append(escape(percent(report.getSuccessRate()))).append("</td><td>").append(valueOrZero(report.getSuccessCount())).append("</td></tr>");
        html.append("<tr><td>Fail</td><td>").append(escape(percent(report.getFailureRate()))).append("</td><td>").append(valueOrZero(report.getFailureCount())).append("</td></tr>");
        html.append("</table></div>");
    }

    private void appendEndpointTable(StringBuilder html) {
        html.append("<h2>Endpoint Aggregates</h2><table><tr><th>Endpoint ID</th><th>Endpoint Name</th><th>Requests</th><th>Avg (ms)</th><th>P90 (ms)</th><th>P95 (ms)</th><th>RPS</th></tr>");
        if (report.getEndpointStats() != null) {
            for (EndpointStats stat : report.getEndpointStats()) {
                html.append("<tr><td>").append(valueOrZero(stat.getEndpointId())).append("</td><td>").append(escape(stat.getEndpointName())).append("</td><td>")
                    .append(valueOrZero(stat.getRequests())).append("</td><td>").append(valueOrZero(stat.getAvgMs())).append("</td><td>")
                    .append(valueOrZero(stat.getP90Ms())).append("</td><td>").append(valueOrZero(stat.getP95Ms())).append("</td><td>")
                    .append(calculateEndpointRps(stat)).append("</td></tr>");
            }
        }
        html.append("</table>");
    }

    private void appendLogTable(StringBuilder html) {
        html.append("<h2>Detailed Logs <span class=\"muted\">(sampled up to 300 rows across the run)</span></h2>");
        html.append("<table><tr><th>ID</th><th>Endpoint ID</th><th>Endpoint Name</th><th>Status</th><th>Response Time (ms)</th><th>Request</th><th>Response</th><th>Timestamp</th></tr>");
        for (RequestLog log : sampledLogs) {
            html.append("<tr><td>").append(valueOrZero(log.getId())).append("</td><td>").append(valueOrZero(log.getEndpointId()))
                .append("</td><td>").append(escape(log.getEndpointName())).append("</td><td>").append(valueOrZero(log.getStatusCode()))
                .append("</td><td>").append(valueOrZero(log.getResponseTime())).append("</td><td><pre>").append(escape(trim(log.getRequest())))
                .append("</pre></td><td><pre>").append(escape(trim(log.getResponse()))).append("</pre></td><td>")
                .append(log.getCreatedAt() == null ? "" : escape(log.getCreatedAt().format(DATE_FORMATTER))).append("</td></tr>");
        }
        html.append("</table>");
    }

    private void chart(StringBuilder html, String title, String[] labels, Map<String, Double[]> seriesValues) {
        int width = 720;
        int height = 320;
        int left = 58;
        int top = 28;
        int right = 14;
        int bottom = 58;
        int plotWidth = width - left - right;
        int plotHeight = height - top - bottom;
        double max = seriesValues.values().stream()
            .flatMap(values -> java.util.Arrays.stream(values == null ? new Double[0] : values))
            .filter(v -> v != null && v > 0)
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(1.0);

        html.append("<div class=\"chart\"><h2>").append(escape(title)).append("</h2><svg viewBox=\"0 0 ").append(width).append(' ').append(height).append("\" width=\"100%\" height=\"320\">");
        html.append("<line x1=\"").append(left).append("\" y1=\"").append(top + plotHeight).append("\" x2=\"").append(left + plotWidth).append("\" y2=\"").append(top + plotHeight).append("\" stroke=\"#9ca3af\"/>");
        html.append("<line x1=\"").append(left).append("\" y1=\"").append(top).append("\" x2=\"").append(left).append("\" y2=\"").append(top + plotHeight).append("\" stroke=\"#9ca3af\"/>");
        html.append("<text x=\"4\" y=\"").append(top + 12).append("\" font-size=\"11\">").append(round(max)).append("</text>");
        int index = 0;
        for (Map.Entry<String, Double[]> entry : seriesValues.entrySet()) {
            html.append("<polyline fill=\"none\" stroke=\"").append(COLORS[index % COLORS.length]).append("\" stroke-width=\"2\" points=\"");
            Double[] values = entry.getValue();
            for (int i = 0; values != null && i < values.length; i++) {
                double x = left + (values.length <= 1 ? 0 : (double) i * plotWidth / (values.length - 1));
                double y = top + plotHeight - (valueOrZero(values[i]) / max * plotHeight);
                html.append(round(x)).append(',').append(round(y)).append(' ');
            }
            html.append("\"/>");
            html.append("<rect x=\"").append(left + index * 120).append("\" y=\"").append(height - 20).append("\" width=\"10\" height=\"10\" fill=\"").append(COLORS[index % COLORS.length]).append("\"/>");
            html.append("<text x=\"").append(left + 14 + index * 120).append("\" y=\"").append(height - 11).append("\" font-size=\"11\">").append(escape(entry.getKey())).append("</text>");
            index++;
        }
        if (labels.length > 0) {
            html.append("<text x=\"").append(left).append("\" y=\"").append(height - 34).append("\" font-size=\"10\">").append(escape(labels[0])).append("</text>");
            html.append("<text x=\"").append(left + plotWidth - 48).append("\" y=\"").append(height - 34).append("\" font-size=\"10\">").append(escape(labels[labels.length - 1])).append("</text>");
        }
        html.append("</svg></div>");
    }

    private void card(StringBuilder html, String label, Object value) {
        html.append("<div class=\"card\"><div class=\"label\">").append(escape(label)).append("</div><div class=\"value\">")
            .append(escape(String.valueOf(value == null ? 0 : value))).append("</div></div>");
    }

    private String[] timeLabels(TreeMap<LocalDateTime, TimeBucket> buckets) {
        return buckets.keySet().stream().map(time -> time.format(TIME_FORMATTER)).toArray(String[]::new);
    }

    private Double[] averageResponseSeries(TreeMap<LocalDateTime, TimeBucket> buckets) {
        return buckets.values().stream().map(TimeBucket::averageResponse).toArray(Double[]::new);
    }

    private Map<String, Double[]> percentileSeries(TreeMap<LocalDateTime, TimeBucket> buckets) {
        Map<String, Double[]> series = new LinkedHashMap<>();
        series.put("P50 ms", percentileResponseSeries(buckets, 50));
        series.put("P90 ms", percentileResponseSeries(buckets, 90));
        series.put("P95 ms", percentileResponseSeries(buckets, 95));
        return series;
    }

    private Double[] percentileResponseSeries(TreeMap<LocalDateTime, TimeBucket> buckets, int percentile) {
        return buckets.values().stream().map(bucket -> bucket.percentileResponse(percentile)).toArray(Double[]::new);
    }

    private Double[] rpsSeries(TreeMap<LocalDateTime, TimeBucket> buckets) {
        return buckets.values().stream().map(bucket -> (double) bucket.requestCount).toArray(Double[]::new);
    }

    private Double[] activeThreadsSeries(TreeMap<LocalDateTime, TimeBucket> buckets) {
        return buckets.values().stream().map(bucket -> (double) bucket.activeThreads).toArray(Double[]::new);
    }

    private Double[] bandwidthSeries(TreeMap<LocalDateTime, TimeBucket> buckets) {
        return buckets.values().stream().map(bucket -> bucket.bytes / 1024.0).toArray(Double[]::new);
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

    private double peakBandwidthKbps() {
        return overallTimeBuckets.values().stream().mapToDouble(bucket -> bucket.bytes / 1024.0).max().orElse(0.0);
    }

    private Double calculateEndpointRps(EndpointStats stat) {
        if (stat.getRequests() == null || report.getDurationSeconds() == null || report.getDurationSeconds() <= 0) return 0.0;
        return round(stat.getRequests() / report.getDurationSeconds());
    }

    private static String endpointLabel(RequestLog logDto) {
        if (logDto.getEndpointName() != null && !logDto.getEndpointName().isBlank()) return logDto.getEndpointName();
        return "Endpoint " + logDto.getEndpointId();
    }

    private static String percent(Double value) {
        return round(valueOrZero(value)) + "%";
    }

    private static String trim(String value) {
        if (value == null) return "";
        return value.length() > 2000 ? value.substring(0, 2000) + "..." : value;
    }

    private static long byteLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static Object valueOrZero(Object value) {
        return value == null ? 0 : value;
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
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
            if (activeThreads != null) this.activeThreads = Math.max(this.activeThreads, activeThreads);
        }

        private void addBytes(long bytes) {
            this.bytes += bytes;
        }

        private double averageResponse() {
            return responseCount == 0 ? 0.0 : responseTimeTotal / responseCount;
        }

        private double percentileResponse(int percentile) {
            if (responseTimes.isEmpty()) return 0.0;
            Collections.sort(responseTimes);
            int index = (int) Math.ceil(percentile / 100.0 * responseTimes.size()) - 1;
            return responseTimes.get(Math.clamp(index, 0, responseTimes.size() - 1));
        }
    }
}
