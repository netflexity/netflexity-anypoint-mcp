package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.mcp.context.AnypointRequestContext;
import com.netflexity.anypoint.mcp.context.TenantTokenCache;
import com.netflexity.anypoint.mcp.model.MonitoringMetric;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class MonitoringClient extends AnypointBaseClient {

    public MonitoringClient(WebClient webClient, TenantTokenCache tokenCache) {
        super(webClient, tokenCache);
    }

    public MonitoringMetric queryMetric(String envId, String appName,
                                         String metric, int lastMinutes) {
        long from = Instant.now().minus(lastMinutes, ChronoUnit.MINUTES).toEpochMilli();
        long to = Instant.now().toEpochMilli();
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.post()
                        .uri(ctx.getBaseUrl() + "/monitoring/query/api/v1/organizations/{orgId}/environments/{envId}/query/metrics",
                                ctx.getOrgId(), envId)
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

    public String listAlerts(String envId) {
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(ctx.getBaseUrl() + "/monitoring/api/v1/organizations/{orgId}/environments/{envId}/alerts",
                                ctx.getOrgId(), envId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(String.class))
                .block();
    }

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
