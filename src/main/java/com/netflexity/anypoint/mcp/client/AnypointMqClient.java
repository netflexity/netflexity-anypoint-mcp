package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.mcp.context.AnypointRequestContext;
import com.netflexity.anypoint.mcp.context.TenantTokenCache;
import com.netflexity.anypoint.mcp.model.MqDestination;
import com.netflexity.anypoint.mcp.model.MqQueueConfig;
import com.netflexity.anypoint.mcp.model.MqQueueStats;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AnypointMqClient extends AnypointBaseClient {

    private static final String DEFAULT_REGION = "us-east-1";

    public AnypointMqClient(WebClient webClient, TenantTokenCache tokenCache) {
        super(webClient, tokenCache);
    }

    public List<MqDestination> listDestinations(String envId, String region) {
        String effectiveRegion = (region != null && !region.isBlank()) ? region : DEFAULT_REGION;
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(ctx.getBaseUrl() + "/mq/admin/api/v1/organizations/{orgId}/environments/{envId}/regions/{region}/destinations",
                                ctx.getOrgId(), envId, effectiveRegion)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(root -> parseDestinations(root, effectiveRegion))
                .block();
    }

    public List<String> listRegions(String envId) {
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(ctx.getBaseUrl() + "/mq/admin/api/v1/organizations/{orgId}/environments/{envId}/regions",
                                ctx.getOrgId(), envId)
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
                Map.of("properties", Map.of("contentType", "application/json"), "body", messageBody)
        );
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.post()
                        .uri(ctx.getBaseUrl() + "/mq/api/v1/organizations/{orgId}/environments/{envId}/regions/{region}/destinations/{destination}/messages",
                                ctx.getOrgId(), envId, effectiveRegion, destination)
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
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.delete()
                        .uri(ctx.getBaseUrl() + "/mq/admin/api/v1/organizations/{orgId}/environments/{envId}/regions/{region}/destinations/{destination}/messages",
                                ctx.getOrgId(), envId, effectiveRegion, destination)
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
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.put()
                        .uri(ctx.getBaseUrl() + "/mq/admin/api/v1/organizations/{orgId}/environments/{envId}/regions/{region}/destinations/{destination}",
                                ctx.getOrgId(), envId, effectiveRegion, destination)
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
        AnypointRequestContext ctx = context();
        return bearerToken(ctx)
                .flatMap(token -> webClient.delete()
                        .uri(ctx.getBaseUrl() + "/mq/admin/api/v1/organizations/{orgId}/environments/{envId}/regions/{region}/destinations/{destination}",
                                ctx.getOrgId(), envId, effectiveRegion, destination)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .thenReturn("Queue deleted")
                        .onErrorReturn("Queue deleted"))
                .block();
    }

    public List<MqQueueStats> getQueueStats(String envId, String region, List<String> queueNames, int periodHours) {
        String effectiveRegion = (region != null && !region.isBlank()) ? region : DEFAULT_REGION;
        int hours = (periodHours > 0 && periodHours <= 168) ? periodHours : 1;
        Instant end = Instant.now();
        Instant start = end.minus(hours, ChronoUnit.HOURS);

        String destinationIds = String.join(",", queueNames);
        AnypointRequestContext ctx = context();
        String uri = UriComponentsBuilder
                .fromUriString(ctx.getBaseUrl() + "/mq/stats/api/v1/organizations/{orgId}/environments/{envId}/regions/{region}/queues")
                .queryParam("destinationIds", destinationIds)
                .queryParam("startDate", start.toString())
                .queryParam("endDate", end.toString())
                .queryParam("period", 600)
                .buildAndExpand(ctx.getOrgId(), envId, effectiveRegion)
                .toUriString();

        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(uri)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(root -> parseQueueStats(root, effectiveRegion))
                .onErrorReturn(List.of())
                .block();
    }

    private List<MqQueueStats> parseQueueStats(JsonNode root, String region) {
        List<MqQueueStats> stats = new ArrayList<>();
        JsonNode destinations = root.isArray() ? root : root.path("destination");
        if (destinations.isMissingNode()) destinations = root;
        for (JsonNode dest : destinations) {
            String name = dest.path("name").asText(dest.path("queueId").asText("unknown"));
            long received = 0, sent = 0, visible = 0;
            for (JsonNode result : dest.path("results")) {
                String type = result.path("type").asText("");
                long total = 0;
                for (JsonNode v : result.path("values")) {
                    total += v.path("value").asLong(0);
                }
                switch (type) {
                    case "messagesReceived" -> received = total;
                    case "messagesSent" -> sent = total;
                    case "messagesVisible" -> visible = total;
                }
            }
            stats.add(MqQueueStats.builder()
                    .name(name)
                    .region(region)
                    .messagesReceived(received)
                    .messagesSent(sent)
                    .messagesVisible(visible)
                    .build());
        }
        return stats;
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
