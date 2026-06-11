package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.mcp.context.AnypointRequestContext;
import com.netflexity.anypoint.mcp.context.TenantTokenCache;
import com.netflexity.anypoint.mcp.model.CloudHub2App;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CloudHub2Client extends AnypointBaseClient {

    public CloudHub2Client(WebClient webClient, TenantTokenCache tokenCache) {
        super(webClient, tokenCache);
    }

    public List<CloudHub2App> listApplications(String envId) {
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(ctx.getBaseUrl() + "/amc/application-manager/api/v2/organizations/{orgId}/environments/{envId}/deployments",
                                ctx.getOrgId(), envId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseApplications)
                .block();
    }

    public CloudHub2App getApplication(String envId, String deploymentId) {
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(ctx.getBaseUrl() + "/amc/application-manager/api/v2/organizations/{orgId}/environments/{envId}/deployments/{id}",
                                ctx.getOrgId(), envId, deploymentId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseApplication)
                .block();
    }

    public String scaleReplicas(String envId, String deploymentId, int replicas) {
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.patch()
                        .uri(ctx.getBaseUrl() + "/amc/application-manager/api/v2/organizations/{orgId}/environments/{envId}/deployments/{id}",
                                ctx.getOrgId(), envId, deploymentId)
                        .header("Authorization", token)
                        .bodyValue(Map.of("target", Map.of("replicas", replicas)))
                        .retrieve()
                        .bodyToMono(String.class)
                        .onErrorReturn("Scale initiated"))
                .block();
    }

    public String restartApplication(String envId, String deploymentId) {
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.post()
                        .uri(ctx.getBaseUrl() + "/amc/application-manager/api/v2/organizations/{orgId}/environments/{envId}/deployments/{id}/restart",
                                ctx.getOrgId(), envId, deploymentId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(String.class)
                        .onErrorReturn("Restart initiated"))
                .block();
    }

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
