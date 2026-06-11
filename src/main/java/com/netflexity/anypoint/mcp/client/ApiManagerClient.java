package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.common.client.AnypointAuthClient;
import com.netflexity.anypoint.mcp.config.AnypointMcpProperties;
import com.netflexity.anypoint.mcp.model.ManagedApi;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

@Component
public class ApiManagerClient extends AnypointBaseClient {

    public ApiManagerClient(WebClient webClient, AnypointAuthClient authClient,
                             AnypointMcpProperties properties) {
        super(webClient, authClient, properties);
    }

    public List<ManagedApi> listApis(String envId) {
        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri("/apimanager/api/v1/organizations/{orgId}/environments/{envId}/apis",
                                orgId(), envId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseApis)
                .block();
    }

    private List<ManagedApi> parseApis(JsonNode root) {
        List<ManagedApi> apis = new ArrayList<>();
        JsonNode assets = root.path("assets");
        if (!assets.isArray()) assets = root;
        for (JsonNode asset : assets) {
            JsonNode apis_ = asset.path("apis");
            Iterable<JsonNode> apiNodes = apis_.isArray() ? apis_ : List.of(asset);
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
