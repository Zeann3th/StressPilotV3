package dev.zeann3th.stresspilot.core.services.metrics;

import dev.zeann3th.stresspilot.core.domain.entities.MetricDefEntity;
import dev.zeann3th.stresspilot.core.domain.entities.MetricScrapeEventEntity;
import dev.zeann3th.stresspilot.core.domain.entities.MetricValueEntity;
import dev.zeann3th.stresspilot.core.domain.entities.RunEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j(topic = "[PrometheusCollectorScraper]")
@Component
@RequiredArgsConstructor
public class PrometheusCollectorScraper {

    private final OkHttpClient httpClient;
    private final MetricDefRegistry defRegistry;

    public MetricScrapeEventEntity scrape(String endpoint, RunEntity run) {
        Request request = new Request.Builder()
                .url(endpoint)
                .header("Accept", "text/plain; version=0.0.4")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Scrape {} returned HTTP {}", endpoint, response.code());
                return null;
            }
            return buildEvent(endpoint, run, response.body().string());
        } catch (IOException e) {
            log.warn("Scrape {} failed: {}", endpoint, e.getMessage());
            return null;
        }
    }

    private MetricScrapeEventEntity buildEvent(String endpoint, RunEntity run, String body) {
        MetricScrapeEventEntity event = MetricScrapeEventEntity.builder()
                .run(run)
                .host(extractHost(endpoint))
                .collectedAt(LocalDateTime.now())
                .source("POLL")
                .values(new ArrayList<>())
                .build();

        Map<String, String> helpMap = new HashMap<>();

        String[] lines = body.split("\n");

        for (String line : lines) {
            line = line.trim();
            if (line.isBlank()) continue;

            if (line.startsWith("# HELP ")) {

                String rest = line.substring(7);
                int space = rest.indexOf(' ');
                if (space > 0) {
                    helpMap.put(rest.substring(0, space), rest.substring(space + 1));
                }
                continue;
            }

            if (line.startsWith("#")) continue;

            ParsedLine parsed = parseLine(line);
            if (parsed == null) continue;

            MetricDefEntity def = defRegistry.getOrCreate(
                    parsed.name(),
                    null,
                    helpMap.get(parsed.name())
            );

            MetricValueEntity value = MetricValueEntity.builder()
                    .event(event)
                    .def(def)
                    .value(parsed.value())
                    .labels(parsed.labelsJson())
                    .build();

            event.getValues().add(value);
        }

        if (event.getValues().isEmpty()) {
            log.warn("Scrape {} produced no metric values — check endpoint", endpoint);
            return null;
        }

        log.debug("Scraped {} values from {}", event.getValues().size(), endpoint);
        return event;
    }

    private ParsedLine parseLine(String line) {

        String[] tokens = line.split("\\s+");
        if (tokens.length < 2) return null;

        String nameWithLabels = tokens[0];
        String valueStr = tokens[1];

        double value;
        try {
            value = Double.parseDouble(valueStr);
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
        } catch (NumberFormatException _) {
            return null;
        }

        int braceOpen = nameWithLabels.indexOf('{');
        if (braceOpen < 0) {

            return new ParsedLine(nameWithLabels, value, null);
        }

        String name = nameWithLabels.substring(0, braceOpen);
        String labelBlock = nameWithLabels.substring(braceOpen + 1,
                nameWithLabels.length() - 1);

        String labelsJson = prometheusLabelsToJson(labelBlock);
        return new ParsedLine(name, value, labelsJson);
    }

    private String prometheusLabelsToJson(String labelBlock) {
        if (labelBlock == null || labelBlock.isBlank()) return null;

        StringBuilder sb = new StringBuilder("{");
        String[] pairs = labelBlock.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i].trim();
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = pair.substring(0, eq);
            String val = pair.substring(eq + 1).replaceAll("^\"|\"$", "");
            sb.append("\"").append(escapeJson(key)).append("\":")
                    .append("\"").append(escapeJson(val)).append("\"");
            if (i < pairs.length - 1) sb.append(",");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractHost(String endpoint) {
        try {
            return new URI(endpoint).getHost();
        } catch (URISyntaxException _) {
            return endpoint;
        }
    }

    private record ParsedLine(String name, double value, String labelsJson) {
    }
}
