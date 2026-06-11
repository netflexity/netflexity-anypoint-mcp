package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.common.client.AnypointAuthClient;
import com.netflexity.anypoint.mcp.config.AnypointMcpProperties;
import com.netflexity.anypoint.mcp.model.Environment;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Component
public class EnvironmentClient extends AnypointBaseClient {

    public EnvironmentClient(WebClient webClient, AnypointAuthClient authClient,
                              AnypointMcpProperties properties) {
        super(webClient, authClient, properties);
    }

    @Cacheable(value = "environments", key = "#root.target.orgId()")
    public List<Environment> listEnvironments() {
        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri("/accounts/api/organizations/{orgId}/environments", orgId())
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::parseEnvironments)
                .block();
    }

    public Environment resolveEnvironment(String envNameOrId) {
        return listEnvironments().stream()
                .filter(e -> e.getName().equalsIgnoreCase(envNameOrId) || e.getId().equals(envNameOrId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + envNameOrId));
    }

    private List<Environment> parseEnvironments(JsonNode root) {
        List<Environment> result = new ArrayList<>();
        JsonNode data = root.path("data");
        if (data.isArray()) {
            for (JsonNode node : data) {
                result.add(Environment.builder()
                        .id(node.path("id").asText())
                        .name(node.path("name").asText())
                        .type(node.path("type").asText())
                        .isProduction(node.path("isProduction").asBoolean())
                        .build());
            }
        }
        return result;
    }
}
