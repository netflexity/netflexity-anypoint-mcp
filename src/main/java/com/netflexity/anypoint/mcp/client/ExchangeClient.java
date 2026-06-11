package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.common.client.AnypointAuthClient;
import com.netflexity.anypoint.mcp.config.AnypointMcpProperties;
import com.netflexity.anypoint.mcp.model.ExchangeAsset;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExchangeClient extends AnypointBaseClient {

    public ExchangeClient(WebClient webClient, AnypointAuthClient authClient,
                           AnypointMcpProperties properties) {
        super(webClient, authClient, properties);
    }

    @Cacheable(value = "exchangeSearch", key = "#query + '-' + #types")
    public List<ExchangeAsset> searchAssets(String query, String types) {
        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri(b -> {
                            var builder = b.path("/exchange/api/v2/assets")
                                    .queryParam("search", query)
                                    .queryParam("limit", 20)
                                    .queryParam("masterOrganizationId", orgId());
                            if (types != null && !types.isBlank()) {
                                builder.queryParam("types", types);
                            }
                            return builder.build();
                        })
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseAssets)
                .block();
    }

    public String getAssetSpec(String groupId, String assetId, String version) {
        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri("/exchange/api/v2/assets/{groupId}/{assetId}/{version}/asset",
                                groupId, assetId, version)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(String.class))
                .block();
    }

    private List<ExchangeAsset> parseAssets(JsonNode root) {
        List<ExchangeAsset> assets = new ArrayList<>();
        Iterable<JsonNode> nodes = root.isArray() ? root : List.of(root);
        for (JsonNode n : nodes) {
            assets.add(ExchangeAsset.builder()
                    .groupId(n.path("groupId").asText())
                    .assetId(n.path("assetId").asText())
                    .version(n.path("version").asText())
                    .name(n.path("name").asText())
                    .type(n.path("type").asText())
                    .description(n.path("description").asText())
                    .createdDate(n.path("createdDate").asText())
                    .build());
        }
        return assets;
    }
}
