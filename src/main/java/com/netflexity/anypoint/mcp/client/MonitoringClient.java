package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.common.client.AnypointAuthClient;
import com.netflexity.anypoint.mcp.config.AnypointMcpProperties;
import com.netflexity.anypoint.mcp.model.MonitoringMetric;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anypoint Monitoring API — queries time-series metrics for CloudHub apps.
 */
@Component
public class MonitoringClient extends AnypointBaseClient {

    public MonitoringClient(WebClient webClient, AnypointAuthClient authClient,
                             AnypointMcpProperties properties) {
        super(webClient, authClient, properties);
    }

    /**
     * Query a metric for a specific app over the last N minutes.
     * Available metrics: cpu, memory, heap, message-count, error-rate, response-time
     */
    public MonitoringMetric queryMetric(String envId, String appName,
                                         String metric, int lastMinutes) {
        long from = Instant.now().minus(lastMinutes, ChronoUnit.MINUTES).toEpochMilli();
        long to = Instant.now().toEpochMilli();

        return bearerToken()
                .flatMap(token -> webClient.post()
                        .uri("/monitoring/query/api/v1/organizations/{orgId}/environments/{envId}/query/metrics",
                                orgId(), envId)
                        .header("Authorization", token)
                        .bodyValue(Map.of(
                                "start", from,
                                "end", to,
                                "step", "1m",
                                "queries", List.of(Map.of(
                                        "metric", metric,
                                        "filters", Map.of("app_id", appName)
                                ))
                        ))
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(root -> parseMetric(root, metric, appName, envId))
                .block();
    }

    /**
     * List active alerts for an environment.
     */
    public String listAlerts(String envId) {
        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri("/monitoring/api/v1/organizations/{orgId}/environments/{envId}/alerts",
                                orgId(), envId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(String.class))
                .block();
    }

    // ── Parser ────────────────────────────────────────────────────────────────

    private MonitoringMetric parseMetric(JsonNode root, String metric, String appName, String envId) {
        List<MonitoringMetric.DataPoint> points = new ArrayList<>();
        JsonNode results = root.path("results");
        if (results.isArray() && !results.isEmpty()) {
            JsonNode series = results.get(0).path("series");
            if (series.isArray()) {
                for (JsonNode s : series) {
                    JsonNode values = s.path("values");
                    if (values.isArray()) {
                        for (JsonNode v : values) {
                            if (v.isArray() && v.size() >= 2) {
                                points.add(MonitoringMetric.DataPoint.builder()
                                        .timestamp(v.get(0).asLong())
                                        .value(v.get(1).asDouble())
                                        .build());
                            }
                        }
                    }
                }
            }
        }
        return MonitoringMetric.builder()
                .metricName(metric)
                .appName(appName)
                .environment(envId)
                .dataPoints(points)
                .build();
    }
}
