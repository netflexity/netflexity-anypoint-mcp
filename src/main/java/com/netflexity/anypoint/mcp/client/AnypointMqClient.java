package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.common.client.AnypointAuthClient;
import com.netflexity.anypoint.mcp.config.AnypointMcpProperties;
import com.netflexity.anypoint.mcp.model.MqDestination;
import com.netflexity.anypoint.mcp.model.MqQueueConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public String sendMessage(String envId, String region, String destination, String messageBody) {
        String effectiveRegion = (region != null && !region.isBlank()) ? region : DEFAULT_REGION;
        List<Map<String, Object>> payload = List.of(
                Map.of(
                        "properties", Map.of("contentType", "application/json"),
                        "body", messageBody
                )
        );
        return bearerToken()
                .flatMap(token -> webClient.post()
                        .uri("/mq/api/v1/organizations/{orgId}/environments/{envId}/regions/{region}/destinations/{destination}/messages",
                                orgId(), envId, effectiveRegion, destination)
                        .header("Authorization", token)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(String.class)
                        .onErrorReturn("Message sent"))
                .thenReturn("Message sent")
                .block();
    }

    public String purgeQueue(String envId, String region, String destination) {
        String effectiveRegion = (region != null && !region.isBlank()) ? region : DEFAULT_REGION;
        return bearerToken()
                .flatMap(token -> webClient.delete()
                        .uri("/mq/admin/api/v1/organizations/{orgId}/environments/{envId}/regions/{region}/destinations/{destination}/messages",
                                orgId(), envId, effectiveRegion, destination)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .thenReturn("Queue purged")
                        .onErrorReturn("Queue purged"))
                .block();
    }

    public MqQueueConfig createQueue(String envId, String region, String destination,
                                      boolean fifo, boolean encrypted) {
        String effectiveRegion = (region != null && !region.isBlank()) ? region : DEFAULT_REGION;
        Map<String, Object> body = Map.of(
                "defaultTtl", 604800000L,
                "defaultLockTtl", 120000L,
                "fifo", fifo,
                "encrypted", encrypted
        );
        return bearerToken()
                .flatMap(token -> webClient.put()
                        .uri("/mq/admin/api/v1/organizations/{orgId}/environments/{envId}/regions/{region}/destinations/{destination}",
                                orgId(), envId, effectiveRegion, destination)
                        .header("Authorization", token)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(n -> MqQueueConfig.builder()
                        .name(n.path("queueId").asText(destination))
                        .region(effectiveRegion)
                        .defaultTtl(n.path("defaultTtl").asLong(604800000L))
                        .defaultLockTtl(n.path("defaultLockTtl").asLong(120000L))
                        .fifo(n.path("fifo").asBoolean(fifo))
                        .encrypted(n.path("encrypted").asBoolean(encrypted))
                        .build())
                .block();
    }

    public String deleteQueue(String envId, String region, String destination) {
        String effectiveRegion = (region != null && !region.isBlank()) ? region : DEFAULT_REGION;
        return bearerToken()
                .flatMap(token -> webClient.delete()
                        .uri("/mq/admin/api/v1/organizations/{orgId}/environments/{envId}/regions/{region}/destinations/{destination}",
                                orgId(), envId, effectiveRegion, destination)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .thenReturn("Queue deleted")
                        .onErrorReturn("Queue deleted"))
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
