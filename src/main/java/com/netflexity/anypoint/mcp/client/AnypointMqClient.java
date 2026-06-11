package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.common.client.AnypointAuthClient;
import com.netflexity.anypoint.mcp.config.AnypointMcpProperties;
import com.netflexity.anypoint.mcp.model.MqDestination;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class AnypointMqClient extends AnypointBaseClient {

    private static final String DEFAULT_REGION = "us-east-1";

    public AnypointMqClient(WebClient webClient, AnypointAuthClient authClient,
                             AnypointMcpProperties properties) {
        super(webClient, authClient, properties);
    }

    public List<MqDestination> listDestinations(String envId, String region) {
        String effectiveRegion = (region != null && !region.isBlank()) ? region : DEFAULT_REGION;
        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri("/mq/admin/api/v1/organizations/{orgId}/environments/{envId}/regions/{region}/destinations",
                                orgId(), envId, effectiveRegion)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(root -> parseDestinations(root, effectiveRegion))
                .block();
    }

    public List<String> listRegions(String envId) {
        return bearerToken()
                .flatMap(token -> webClient.get()
                        .uri("/mq/admin/api/v1/organizations/{orgId}/environments/{envId}/regions",
                                orgId(), envId)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(root -> {
                    List<String> regions = new ArrayList<>();
                    if (root.isArray()) {
                        root.forEach(n -> regions.add(n.path("id").asText()));
                    }
                    return regions;
                })
                .block();
    }

    private List<MqDestination> parseDestinations(JsonNode root, String region) {
        List<MqDestination> destinations = new ArrayList<>();
        Iterable<JsonNode> nodes = root.isArray() ? root : List.of(root);
        for (JsonNode n : nodes) {
            String type = n.path("type").asText("queue");
            destinations.add(MqDestination.builder()
                    .name(n.path("queueId").asText(n.path("exchangeId").asText()))
                    .type(type)
                    .region(region)
                    .messageCount(n.path("stats").path("messagesInQueue").asLong(-1))
                    .inFlight(n.path("stats").path("messagesInFlight").asLong(-1))
                    .fifo(n.path("fifo").asBoolean(false))
                    .encrypted(n.path("encrypted").asBoolean(false))
                    .deadLetterQueueId(n.path("defaultDeadLetterQueueId").asText(""))
                    .build());
        }
        return destinations;
    }
}
