package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.AnypointMqClient;
import com.netflexity.anypoint.mcp.client.EnvironmentClient;
import com.netflexity.anypoint.mcp.context.AnypointContextHolder;
import com.netflexity.anypoint.mcp.license.LicenseService;
import com.netflexity.anypoint.mcp.model.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class DlqTools {

    private final AnypointMqClient mqClient;
    private final EnvironmentClient environmentClient;
    private final LicenseService licenseService;

    public DlqTools(AnypointMqClient mqClient, EnvironmentClient environmentClient,
                    LicenseService licenseService) {
        this.mqClient = mqClient;
        this.environmentClient = environmentClient;
        this.licenseService = licenseService;
    }

    private String apiKey() {
        return AnypointContextHolder.require().getApiKey();
    }

    @Tool(description = """
            Triage every dead-letter queue (DLQ) in an environment: find which queues are
            accumulating failed messages, how deep they are, and cluster the failures by
            signature so you can see the top failure modes at a glance.
            This is non-destructive: it peeks messages and immediately releases the locks,
            so real consumers are unaffected.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - region: AWS region (e.g. "us-east-1"). Defaults to "us-east-1".
              - maxPerQueue: how many messages to sample per DLQ for clustering (1-10, default 10)
            Returns, per non-empty DLQ: the DLQ name, the source queue it belongs to,
            message counts, and the top error clusters {signature, count, sampleMessageId}.
            Sorted by depth (most backed-up DLQ first). Free tier: this is a diagnosis tool.
            To automatically replay the recoverable messages, use redriveMessages (Pro).
            """)
    public List<DlqTriageReport> triageDeadLetterQueues(String environment, String region, int maxPerQueue) {
        // FREE diagnosis tool: surfaces the pain; redriveMessages (PRO) is the fix.
        String envId = environmentClient.resolveEnvironment(environment).getId();
        int sample = Math.min(Math.max(maxPerQueue, 1), 10);

        List<MqDestination> destinations = mqClient.listDestinations(envId, region);

        // Map DLQ name -> source queue, using each destination's deadLetterQueueId.
        Map<String, String> dlqToSource = new LinkedHashMap<>();
        for (MqDestination d : destinations) {
            String dlqId = d.getDeadLetterQueueId();
            if (dlqId != null && !dlqId.isBlank()) {
                dlqToSource.putIfAbsent(dlqId, d.getName());
            }
        }
        if (dlqToSource.isEmpty()) return List.of();

        // Only DLQs that actually exist as destinations, with live depth.
        Set<String> existing = destinations.stream().map(MqDestination::getName).collect(Collectors.toSet());
        List<String> dlqNames = dlqToSource.keySet().stream().filter(existing::contains).collect(Collectors.toList());
        if (dlqNames.isEmpty()) return List.of();

        List<MqQueueDepth> depths = mqClient.getQueueDepth(envId, region, dlqNames);
        Map<String, MqQueueDepth> depthByName = depths.stream()
                .collect(Collectors.toMap(MqQueueDepth::getName, d -> d, (a, b) -> a));

        List<DlqTriageReport> reports = new ArrayList<>();
        for (String dlq : dlqNames) {
            MqQueueDepth depth = depthByName.get(dlq);
            long inQueue = depth != null ? depth.getMessagesInQueue() : 0L;
            long inFlight = depth != null ? depth.getMessagesInFlight() : 0L;
            if (inQueue <= 0) continue; // skip empty DLQs

            List<MqMessage> samples = mqClient.peekMessages(envId, region, dlq, sample);
            reports.add(DlqTriageReport.builder()
                    .dlqName(dlq)
                    .sourceQueue(dlqToSource.get(dlq))
                    .region((region != null && !region.isBlank()) ? region : "us-east-1")
                    .messagesInQueue(inQueue)
                    .messagesInFlight(inFlight)
                    .sampled(samples.size())
                    .topErrors(cluster(samples))
                    .build());
        }
        reports.sort(Comparator.comparingLong(DlqTriageReport::getMessagesInQueue).reversed());
        return reports;
    }

    @Tool(description = """
            Redrive (replay) messages from a dead-letter queue back to a target destination.
            Use this to recover after an outage: once the downstream issue is fixed, replay the
            failed messages instead of losing them. MuleSoft has no native redrive for Anypoint MQ.
            IMPORTANT: this works by consuming from the DLQ and re-publishing to the target, so it is
            at-least-once and REPUBLISHED MESSAGES GET NEW messageIds. A message is only removed from
            the DLQ after it is successfully re-published; failures are left in place.
            Always run with dryRun=true first to see how many messages would move.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - region: AWS region (e.g. "us-east-1"). Defaults to "us-east-1".
              - dlqQueue: the dead-letter queue to drain from
              - targetDestination: the queue or exchange to replay messages into (often the source queue)
              - maxMessages: maximum messages to redrive (1-1000)
              - dryRun: if true, only inspect and count; publish nothing and remove nothing
            Returns attempted/redriven/failed counts and a note. Requires a Pro license.
            """)
    public RedriveResult redriveMessages(String environment, String region, String dlqQueue,
                                         String targetDestination, int maxMessages, boolean dryRun) {
        licenseService.requirePro(apiKey());
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return mqClient.redrive(envId, region, dlqQueue, targetDestination, maxMessages, dryRun);
    }

    private List<ErrorCluster> cluster(List<MqMessage> messages) {
        Map<String, List<MqMessage>> groups = new LinkedHashMap<>();
        for (MqMessage m : messages) {
            groups.computeIfAbsent(signatureOf(m), k -> new ArrayList<>()).add(m);
        }
        return groups.entrySet().stream()
                .map(e -> {
                    MqMessage sample = e.getValue().get(0);
                    String body = sample.getBody() == null ? "" : sample.getBody();
                    if (body.length() > 200) body = body.substring(0, 200) + "…";
                    return ErrorCluster.builder()
                            .signature(e.getKey())
                            .count(e.getValue().size())
                            .sampleMessageId(sample.getMessageId())
                            .sampleBody(body)
                            .build();
                })
                .sorted(Comparator.comparingLong(ErrorCluster::getCount).reversed())
                .collect(Collectors.toList());
    }

    // Best-effort failure signature: prefer an app-set error property, fall back to a
    // digit-normalized body prefix so similar failures cluster together.
    private String signatureOf(MqMessage m) {
        Map<String, Object> p = m.getProperties();
        if (p != null) {
            for (String k : List.of("errorType", "exceptionType", "error", "failureReason", "x-mule-error")) {
                Object v = p.get(k);
                if (v != null && !v.toString().isBlank()) {
                    return truncate(v.toString());
                }
            }
        }
        String body = m.getBody() == null ? "" : m.getBody();
        String norm = body.replaceAll("\\d+", "#").trim();
        if (norm.length() > 60) norm = norm.substring(0, 60);
        return norm.isBlank() ? "(empty body)" : norm;
    }

    private String truncate(String s) {
        return s.length() > 80 ? s.substring(0, 80) : s;
    }
}
