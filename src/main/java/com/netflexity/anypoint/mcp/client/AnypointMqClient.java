package com.netflexity.anypoint.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.netflexity.anypoint.mcp.context.AnypointRequestContext;
import com.netflexity.anypoint.mcp.context.TenantTokenCache;
import com.netflexity.anypoint.mcp.model.*;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AnypointMqClient extends AnypointBaseClient {

    private static final String DEFAULT_REGION = "us-east-1";

    private static final Map<String, String> BROKER_URLS = Map.of(
            "us-east-1",      "https://mq-us-east-1.anypoint.mulesoft.com",
            "us-west-2",      "https://mq-us-west-2.anypoint.mulesoft.com",
            "eu-west-1",      "https://mq-eu-west-1.anypoint.mulesoft.com",
            "eu-central-1",   "https://mq-eu-central-1.anypoint.mulesoft.com",
            "ap-southeast-1", "https://mq-ap-southeast-1.anypoint.mulesoft.com",
            "ap-southeast-2", "https://mq-ap-southeast-2.anypoint.mulesoft.com",
            "ca-central-1",   "https://mq-ca-central-1.anypoint.mulesoft.com"
    );

    private String brokerUrl(String region) {
        return BROKER_URLS.getOrDefault(region, "https://mq-us-east-1.anypoint.mulesoft.com");
    }

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

    public List<MqQueueDepth> getQueueDepth(String envId, String region, List<String> queueNames) {
        String effectiveRegion = (region != null && !region.isBlank()) ? region : DEFAULT_REGION;
        String destinationIds = queueNames.stream()
                .map(q -> URLEncoder.encode(q, StandardCharsets.UTF_8))
                .collect(Collectors.joining(","));
        AnypointRequestContext ctx = context();
        String uri = ctx.getBaseUrl()
                + "/mq/stats/api/v1/organizations/" + ctx.getOrgId()
                + "/environments/" + envId
                + "/regions/" + effectiveRegion
                + "/queues?destinationIds=" + destinationIds;

        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(uri)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {}))
                .map(list -> list.stream().map(entry -> {
                    Object msgs = entry.get("messages");
                    Object inflight = entry.get("inflightMessages");
                    return MqQueueDepth.builder()
                            .name(String.valueOf(entry.getOrDefault("destination", "")))
                            .region(effectiveRegion)
                            .messagesInQueue(msgs instanceof Number n ? n.longValue() : 0L)
                            .messagesInFlight(inflight instanceof Number n ? n.longValue() : 0L)
                            .build();
                }).collect(Collectors.toList()))
                .onErrorReturn(List.of())
                .block();
    }

    public MqUsageSummary getUsageStats(String envId, int lookbackDays) {
        int days = (lookbackDays > 0 && lookbackDays <= 90) ? lookbackDays : 30;
        Instant end = Instant.now();
        Instant start = end.minus(days, ChronoUnit.DAYS);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
        AnypointRequestContext ctx = context();
        String uri = ctx.getBaseUrl()
                + "/mq/stats/api/v1/organizations/" + ctx.getOrgId()
                + "/environments/" + envId
                + "?startDate=" + fmt.format(start)
                + "&endDate=" + fmt.format(end)
                + "&period=1day";

        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(uri)
                        .header("Authorization", token)
                        .retrieve()
                        .bodyToFlux(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .collectList())
                .map(list -> {
                    long receipts = 0, billable = 0, apiRequests = 0, bytes = 0;
                    for (Map<String, Object> day : list) {
                        receipts    += toLong(day.get("messageReceiptCount"));
                        billable    += toLong(day.get("billableUnitCount"));
                        apiRequests += toLong(day.get("apiRequestCount"));
                        bytes       += toLong(day.get("messageByteCount"));
                    }
                    return MqUsageSummary.builder()
                            .periodDays(days)
                            .totalMessageReceipts(receipts)
                            .totalBillableUnits(billable)
                            .totalApiRequests(apiRequests)
                            .totalMessageBytes(bytes)
                            .build();
                })
                .onErrorReturn(MqUsageSummary.builder().periodDays(days).build())
                .block();
    }

    public List<MqAuditEvent> getMqAuditLog(int lookbackHours) {
        int hours = (lookbackHours > 0 && lookbackHours <= 720) ? lookbackHours : 24;
        Instant end = Instant.now();
        Instant start = end.minus(hours, ChronoUnit.HOURS);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
        AnypointRequestContext ctx = context();
        String uri = ctx.getBaseUrl() + "/audit/v2/organizations/" + ctx.getOrgId() + "/query";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("startDate", fmt.format(start));
        body.put("endDate", fmt.format(end));
        body.put("platforms", List.of("Anypoint MQ"));
        body.put("objectTypes", List.of());
        body.put("actions", List.of());
        body.put("objectIds", List.of());
        body.put("userIds", List.of());
        body.put("ascending", false);
        body.put("organizationId", ctx.getOrgId());
        body.put("offset", 0);
        body.put("limit", 100);

        return bearerToken(ctx)
                .flatMap(token -> webClient.post()
                        .uri(uri)
                        .header("Authorization", token)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(root -> {
                    List<MqAuditEvent> events = new ArrayList<>();
                    JsonNode data = root.path("data");
                    if (data.isArray()) {
                        for (JsonNode n : data) {
                            events.add(MqAuditEvent.builder()
                                    .id(n.path("id").asText(""))
                                    .action(n.path("action").asText(""))
                                    .objectName(n.path("objectName").asText(""))
                                    .objectType(n.path("objectType").asText(""))
                                    .userName(n.path("userName").asText(""))
                                    .userEmail(n.path("userEmail").asText(""))
                                    .createdAt(n.path("createdAt").asText(""))
                                    .environmentName(n.path("environmentName").asText(""))
                                    .build());
                        }
                    }
                    return events;
                })
                .onErrorReturn(List.of())
                .block();
    }

    @SuppressWarnings("unchecked")
    public List<MqMessage> peekMessages(String envId, String region, String queueName, int maxMessages) {
        String effectiveRegion = (region != null && !region.isBlank()) ? region : DEFAULT_REGION;
        int batchSize = Math.min(Math.max(maxMessages, 1), 10);
        AnypointRequestContext ctx = context();
        String browseUrl = brokerUrl(effectiveRegion)
                + "/api/v1/organizations/" + ctx.getOrgId()
                + "/environments/" + envId
                + "/destinations/" + queueName
                + "/messages?batchSize=" + batchSize + "&pollingTime=1000&lockTtl=10000";

        return bearerToken(ctx)
                .flatMap(token -> webClient.get()
                        .uri(browseUrl)
                        .header("Authorization", token)
                        .header("X-ANYPNT-ORG-ID", ctx.getOrgId())
                        .header("X-ANYPNT-ENV-ID", envId)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                        .defaultIfEmpty(List.of())
                        .flatMap(messages -> {
                            List<MqMessage> result = new ArrayList<>();
                            List<Map<String, Object>> lockInfos = new ArrayList<>();
                            for (Map<String, Object> msg : messages) {
                                Map<String, Object> headers = (Map<String, Object>) msg.getOrDefault("headers", Map.of());
                                String msgId = (String) headers.get("messageId");
                                String lockId = (String) headers.get("lockId");
                                String contentType = (String) headers.get("contentType");
                                Map<String, Object> props = (Map<String, Object>) msg.getOrDefault("properties", Map.of());
                                Object bodyObj = msg.get("body");
                                String body = bodyObj != null ? bodyObj.toString() : "";
                                if (body.length() > 500) body = body.substring(0, 500) + "…";
                                result.add(MqMessage.builder()
                                        .messageId(msgId)
                                        .lockId(lockId)
                                        .contentType(contentType)
                                        .body(body)
                                        .properties(props)
                                        .build());
                                if (msgId != null && lockId != null) {
                                    Map<String, Object> lock = new LinkedHashMap<>();
                                    lock.put("messageId", msgId);
                                    lock.put("lockId", lockId);
                                    lockInfos.add(lock);
                                }
                            }
                            // Release locks so messages remain available for real consumers
                            if (lockInfos.isEmpty()) return reactor.core.publisher.Mono.just(result);
                            String releaseUrl = brokerUrl(effectiveRegion)
                                    + "/api/v1/organizations/" + ctx.getOrgId()
                                    + "/environments/" + envId
                                    + "/destinations/" + queueName
                                    + "/messages/locks";
                            // Release in batches of 5
                            List<List<Map<String, Object>>> batches = new ArrayList<>();
                            for (int i = 0; i < lockInfos.size(); i += 5)
                                batches.add(lockInfos.subList(i, Math.min(i + 5, lockInfos.size())));
                            return reactor.core.publisher.Flux.fromIterable(batches)
                                    .concatMap(batch -> webClient
                                            .method(org.springframework.http.HttpMethod.DELETE)
                                            .uri(releaseUrl)
                                            .header("Authorization", token)
                                            .header("X-ANYPNT-ORG-ID", ctx.getOrgId())
                                            .header("X-ANYPNT-ENV-ID", envId)
                                            .bodyValue(batch)
                                            .retrieve()
                                            .bodyToMono(Void.class)
                                            .onErrorResume(e -> reactor.core.publisher.Mono.empty()))
                                    .then(reactor.core.publisher.Mono.just(result));
                        }))
                .onErrorReturn(List.of())
                .block();
    }

    private long toLong(Object val) {
        return val instanceof Number n ? n.longValue() : 0L;
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
