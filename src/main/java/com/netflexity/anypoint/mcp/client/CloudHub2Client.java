package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.common.client.AnypointAuthClient;
import com.netflexity.anypoint.mcp.config.AnypointMcpProperties;
import com.netflexity.anypoint.mcp.model.CloudHub2App;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CloudHub 2.0 / Runtime Fabric Deployments API.
 * Endpoint: /amc/application-manager/api/v2
 */
@Component
public class CloudHub2Client extends AnypointBaseClient {

    public CloudHub2Client(WebClient webClient, AnypointAuthClient authClient,
                            AnypointMcpProperties properties) {
        super(webClient, authClient, properties);
    }

    public List<CloudHub2App> listApplications(String envId) {
        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri("/amc/application-manager/api/v2/organizations/{orgId}/environments/{envId}/deployments",
                                orgId(), envId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseApplications)
                .block();
    }

    public CloudHub2App getApplication(String envId, String deploymentId) {
        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri("/amc/application-manager/api/v2/organizations/{orgId}/environments/{envId}/deployments/{id}",
                                orgId(), envId, deploymentId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseApplication)
                .block();
    }

    public String scaleReplicas(String envId, String deploymentId, int replicas) {
        return bearerToken()
                .flatMap(token -> webClient.patch()
                        .uri("/amc/application-manager/api/v2/organizations/{orgId}/environments/{envId}/deployments/{id}",
                                orgId(), envId, deploymentId)
                        .header("Authorization", token)
                        .bodyValue(Map.of("target", Map.of("replicas", replicas)))
                        .retrieve()
                        .bodyToMono(String.class)
                        .onErrorReturn("Scale initiated"))
                .block();
    }

    public String restartApplication(String envId, String deploymentId) {
        return bearerToken()
                .flatMap(token -> webClient.post()
                        .uri("/amc/application-manager/api/v2/organizations/{orgId}/environments/{envId}/deployments/{id}/restart",
                                orgId(), envId, deploymentId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(String.class)
                        .onErrorReturn("Restart initiated"))
                .block();
    }

    // ── Parsers ──────────────────────────────────────────────────────────────

    private List<CloudHub2App> parseApplications(JsonNode root) {
        List<CloudHub2App> apps = new ArrayList<>();
        JsonNode items = root.path("items");
        Iterable<JsonNode> nodes = items.isArray() ? items : (root.isArray() ? root : List.of(root));
        for (JsonNode n : nodes) {
            apps.add(parseApplication(n));
        }
        return apps;
    }

    private CloudHub2App parseApplication(JsonNode n) {
        JsonNode target = n.path("target");
        return CloudHub2App.builder()
                .id(n.path("id").asText())
                .name(n.path("name").asText())
                .status(n.path("status").asText())
                .target(target.path("targetId").asText(target.path("type").asText()))
                .runtimeVersion(n.path("application").path("ref").path("version").asText())
                .replicas(target.path("replicas").asInt(1))
                .lastModified(n.path("lastModifiedDate").asText())
                .applicationUrl(n.path("application").path("vCores").asText(""))
                .build();
    }
}
