package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.mcp.context.AnypointRequestContext;
import com.netflexity.anypoint.mcp.context.TenantTokenCache;
import com.netflexity.anypoint.mcp.model.ExchangeAsset;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Component
public class ExchangeClient extends AnypointBaseClient {

    public ExchangeClient(WebClient webClient, TenantTokenCache tokenCache) {
        super(webClient, tokenCache);
    }

    @Cacheable(value = "exchangeSearch",
               key = "#root.target.context().getOrgId() + '-' + #query + '-' + #types")
    public List<ExchangeAsset> searchAssets(String query, String types) {
        AnypointRequestContext ctx = context();
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(ctx.getBaseUrl() + "/exchange/api/v2/assets")
                .queryParam("search", query)
                .queryParam("limit", 20)
                .queryParam("masterOrganizationId", ctx.getOrgId());
        if (types != null && !types.isBlank()) {
            uriBuilder.queryParam("types", types);
        }
        String uri = uriBuilder.toUriString();

        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(uri)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseAssets)
                .block();
    }

    public String getAssetSpec(String groupId, String assetId, String version) {
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(ctx.getBaseUrl() + "/exchange/api/v2/assets/{groupId}/{assetId}/{version}/asset",
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
