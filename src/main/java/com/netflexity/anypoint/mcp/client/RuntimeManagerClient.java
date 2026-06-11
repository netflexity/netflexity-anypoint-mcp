package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.common.client.AnypointAuthClient;
import com.netflexity.anypoint.mcp.config.AnypointMcpProperties;
import com.netflexity.anypoint.mcp.model.MuleApp;
import com.netflexity.anypoint.mcp.model.MuleAppLog;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Covers CloudHub 1.0 Runtime Manager API.
 * CH2/RTF support is a v2 addition.
 */
@Component
public class RuntimeManagerClient extends AnypointBaseClient {

    public RuntimeManagerClient(WebClient webClient, AnypointAuthClient authClient,
                                  AnypointMcpProperties properties) {
        super(webClient, authClient, properties);
    }

    public List<MuleApp> listApplications(String envId) {
        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri("/cloudhub/api/v2/applications")
                        .header("Authorization", token)
                        .header("X-ANYPNT-ORG-ID", orgId())
                        .header("X-ANYPNT-ENV-ID", envId)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseApplications)
                .block();
    }

    public MuleApp getApplication(String envId, String appName) {
        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri("/cloudhub/api/v2/applications/{domain}", appName)
                        .header("Authorization", token)
                        .header("X-ANYPNT-ORG-ID", orgId())
                        .header("X-ANYPNT-ENV-ID", envId)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseApplication)
                .block();
    }

    public List<MuleAppLog> getApplicationLogs(String envId, String appName,
                                                int lines, int sinceMinutes) {
        long startMs = Instant.now().minus(sinceMinutes, ChronoUnit.MINUTES).toEpochMilli();
        long endMs = Instant.now().toEpochMilli();

        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri(b -> b.path("/cloudhub/api/v2/applications/{domain}/logs")
                                .queryParam("startDate", startMs)
                                .queryParam("endDate", endMs)
                                .queryParam("limit", lines)
                                .build(appName))
                        .header("Authorization", token)
                        .header("X-ANYPNT-ORG-ID", orgId())
                        .header("X-ANYPNT-ENV-ID", envId)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseLogs)
                .block();
    }

    public String restartApplication(String envId, String appName) {
        return bearerToken()
                .flatMap(token -> webClient.post()
                        .uri("/cloudhub/api/v2/applications/{domain}/status", appName)
                        .header("Authorization", token)
                        .header("X-ANYPNT-ORG-ID", orgId())
                        .header("X-ANYPNT-ENV-ID", envId)
                        .bodyValue(Map.of("status", "restart"))
                        .retrieve()
                        .bodyToMono(String.class)
                        .onErrorReturn("Restart initiated"))
                .block();
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private List<MuleApp> parseApplications(JsonNode root) {
        List<MuleApp> apps = new ArrayList<>();
        // CH1 returns an array at top level
        Iterable<JsonNode> nodes = root.isArray() ? root : List.of(root);
        for (JsonNode n : nodes) {
            apps.add(parseApplication(n));
        }
        return apps;
    }

    private MuleApp parseApplication(JsonNode n) {
        String workers = "";
        JsonNode w = n.path("workers");
        if (!w.isMissingNode()) {
            workers = w.path("amount").asInt(1) + "x" + w.path("type").path("name").asText("");
        }
        return MuleApp.builder()
                .name(n.path("domain").asText(n.path("name").asText()))
                .status(n.path("status").asText())
                .runtimeVersion(n.path("muleVersion").path("version").asText(n.path("runtimeVersion").asText()))
                .workers(workers)
                .region(n.path("region").asText())
                .lastUpdated(n.path("lastUpdateTime").asLong(0))
                .fullUrl(n.path("fullDomain").asText())
                .build();
    }

    private List<MuleAppLog> parseLogs(JsonNode root) {
        List<MuleAppLog> logs = new ArrayList<>();
        Iterable<JsonNode> nodes = root.isArray() ? root : root.path("logs");
        for (JsonNode n : (Iterable<JsonNode>) (root.isArray() ? root : root.path("data"))) {
            logs.add(MuleAppLog.builder()
                    .timestamp(n.path("event").path("timestamp").asLong(n.path("timestamp").asLong()))
                    .level(n.path("event").path("priority").asText(n.path("level").asText("INFO")))
                    .message(n.path("event").path("message").asText(n.path("message").asText()))
                    .build());
        }
        return logs;
    }
}
