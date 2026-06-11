package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.mcp.context.AnypointRequestContext;
import com.netflexity.anypoint.mcp.context.TenantTokenCache;
import com.netflexity.anypoint.mcp.model.ManagedApi;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ApiManagerClient extends AnypointBaseClient {

    public ApiManagerClient(WebClient webClient, TenantTokenCache tokenCache) {
        super(webClient, tokenCache);
    }

    public List<ManagedApi> listApis(String envId) {
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(ctx.getBaseUrl() + "/apimanager/api/v1/organizations/{orgId}/environments/{envId}/apis",
                                ctx.getOrgId(), envId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseApis)
                .block();
    }

    public String applyPolicy(String envId, String apiId, String policyTemplateId,
                               Map<String, Object> configurationData) {
        Map<String, Object> body = Map.of(
                "policyTemplateId", policyTemplateId,
                "configurationData", configurationData != null ? configurationData : Map.of(),
                "pointcutData", List.of()
        );
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.post()
                        .uri(ctx.getBaseUrl() + "/apimanager/api/v1/organizations/{orgId}/environments/{envId}/apis/{apiId}/policies",
                                ctx.getOrgId(), envId, apiId)
                        .header("Authorization", token)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class))
                .block();
    }

    public String removePolicy(String envId, String apiId, String policyId) {
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.delete()
                        .uri(ctx.getBaseUrl() + "/apimanager/api/v1/organizations/{orgId}/environments/{envId}/apis/{apiId}/policies/{policyId}",
                                ctx.getOrgId(), envId, apiId, policyId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .thenReturn("Policy removed"))
                .block();
    }

    private List<ManagedApi> parseApis(JsonNode root) {
        List<ManagedApi> apis = new ArrayList<>();
        JsonNode assets = root.path("assets");
        if (!assets.isArray()) assets = root;
        for (JsonNode asset : assets) {
            JsonNode apiNodes_ = asset.path("apis");
            Iterable<JsonNode> apiNodes = apiNodes_.isArray() ? apiNodes_ : List.of(asset);
            for (JsonNode n : apiNodes) {
                List<String> policies = new ArrayList<>();
                JsonNode pol = n.path("policies");
                if (pol.isArray()) {
                    pol.forEach(p -> policies.add(p.path("policyTemplateId").asText()));
                }
                apis.add(ManagedApi.builder()
                        .id(n.path("id").asText())
                        .name(asset.path("exchangeAssetName").asText(n.path("assetId").asText()))
                        .version(n.path("assetVersion").asText())
                        .endpoint(n.path("endpoint").path("uri").asText())
                        .status(n.path("status").asText())
                        .policies(policies)
                        .build());
            }
        }
        return apis;
    }
}
