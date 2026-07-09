package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.*;
import com.netflexity.anypoint.mcp.context.AnypointContextHolder;
import com.netflexity.anypoint.mcp.license.LicenseService;
import com.netflexity.anypoint.mcp.model.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FinOpsTools {

    private static final long BILLABLE_UNIT_BYTES = 100_000L; // 1 billable unit per ~100KB received
    private static final double UNDERUTILIZED_CPU = 30.0;
    private static final double HOT_CPU = 85.0;

    private final EnvironmentClient environmentClient;
    private final AnypointMqClient mqClient;
    private final MonitoringClient monitoringClient;
    private final RuntimeManagerClient runtimeManagerClient;
    private final CloudHub2Client cloudHub2Client;
    private final LicenseService licenseService;

    public FinOpsTools(EnvironmentClient environmentClient,
                       AnypointMqClient mqClient,
                       MonitoringClient monitoringClient,
                       RuntimeManagerClient runtimeManagerClient,
                       CloudHub2Client cloudHub2Client,
                       LicenseService licenseService) {
        this.environmentClient = environmentClient;
        this.mqClient = mqClient;
        this.monitoringClient = monitoringClient;
        this.runtimeManagerClient = runtimeManagerClient;
        this.cloudHub2Client = cloudHub2Client;
        this.licenseService = licenseService;
    }

    private String apiKey() {
        return AnypointContextHolder.require().getApiKey();
    }

    @Tool(description = """
            Analyze Anypoint MQ cost and waste for an environment and estimate spend.
            Combines billing usage (billable units, bytes, API requests) with per-queue activity to
            surface idle queues and oversized-payload waste, then estimates a dollar figure.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - lookbackDays: billing window to aggregate (1-90, default 30)
              - dollarsPerBillableUnit: YOUR contracted price per MQ billable unit. MuleSoft does not
                expose this via API, so pass your rate. If 0 or unknown, the dollar estimate is omitted.
            Returns billable units, estimated cost, average message size, idle queues, and waste findings.
            Requires a Pro license.
            """)
    public MqCostReport analyzeMqCosts(String environment, int lookbackDays, double dollarsPerBillableUnit) {
        licenseService.requirePro(apiKey());
        String envId = environmentClient.resolveEnvironment(environment).getId();
        int days = (lookbackDays > 0 && lookbackDays <= 90) ? lookbackDays : 30;

        MqUsageSummary usage = mqClient.getUsageStats(envId, days);
        long billableUnits = usage.getTotalBillableUnits();
        long receipts = usage.getTotalMessageReceipts();
        long bytes = usage.getTotalMessageBytes();
        long apiRequests = usage.getTotalApiRequests();
        long avgBytes = receipts > 0 ? bytes / receipts : 0;
        double estCost = dollarsPerBillableUnit > 0 ? billableUnits * dollarsPerBillableUnit : 0.0;

        int idleWindowHours = Math.min(days * 24, 168);
        List<String> idleQueues = findIdleQueues(envId, idleWindowHours);

        List<String> findings = new ArrayList<>();
        if (!idleQueues.isEmpty()) {
            findings.add(idleQueues.size() + " queue(s) received zero messages in the last "
                    + idleWindowHours + "h — candidates for deletion.");
        }
        if (avgBytes > BILLABLE_UNIT_BYTES) {
            findings.add("Average message is " + avgBytes + " bytes (> " + BILLABLE_UNIT_BYTES
                    + "): each such message costs multiple billable units. Trim payloads or compress.");
        }
        if (receipts > 0 && apiRequests > receipts * 3) {
            findings.add(apiRequests + " API requests for only " + receipts
                    + " messages received — likely empty consumer polls inflating request cost.");
        }
        if (findings.isEmpty()) findings.add("No obvious MQ cost waste detected in this window.");

        String summary = "Over " + days + " days: " + billableUnits + " billable units"
                + (dollarsPerBillableUnit > 0 ? " ≈ $" + String.format(Locale.US, "%.2f", estCost) : " (set dollarsPerBillableUnit for a $ estimate)")
                + "; " + idleQueues.size() + " idle queue(s).";

        return MqCostReport.builder()
                .periodDays(days)
                .billableUnits(billableUnits)
                .dollarsPerBillableUnit(dollarsPerBillableUnit)
                .estimatedCost(estCost)
                .messageReceipts(receipts)
                .messageBytes(bytes)
                .avgMessageBytes(avgBytes)
                .apiRequests(apiRequests)
                .idleWindowHours(idleWindowHours)
                .idleQueues(idleQueues)
                .findings(findings)
                .summary(summary)
                .build();
    }

    @Tool(description = """
            Recommend rightsizing for CloudHub applications by comparing provisioned capacity against
            observed CPU/memory utilization from Anypoint Monitoring. Flags underutilized apps that can
            be downsized (reclaimable capacity) and hot apps that should NOT be downsized.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - lookbackDays: utilization window (1-1, capped at 1 day by Anypoint Monitoring; default 1)
            Returns per-app avg/max CPU, current config, and a recommendation with a confidence level.
            Apps without monitoring data are reported with confidence NONE. Requires a Pro license.
            """)
    public List<RightsizeRecommendation> rightsizeWorkers(String environment, int lookbackDays) {
        licenseService.requirePro(apiKey());
        String envId = environmentClient.resolveEnvironment(environment).getId();
        int clampedDays = Math.min(Math.max(lookbackDays, 1), 1); // Monitoring caps the window at 1 day
        int minutes = clampedDays * 1440;

        List<RightsizeRecommendation> recs = new ArrayList<>();

        try {
            for (MuleApp a : runtimeManagerClient.listApplications(envId)) {
                if (!isRunning(a.getStatus())) continue;
                recs.add(recommend("CH1", a.getName(), a.getStatus(),
                        "workers: " + a.getWorkers(), envId, minutes, 1));
            }
        } catch (Exception ignored) { }

        try {
            for (CloudHub2App a : cloudHub2Client.listApplications(envId)) {
                if (!isRunning(a.getStatus())) continue;
                recs.add(recommend("CH2", a.getName(), a.getStatus(),
                        "replicas: " + a.getReplicas(), envId, minutes, a.getReplicas()));
            }
        } catch (Exception ignored) { }

        return recs;
    }

    private RightsizeRecommendation recommend(String platform, String appName, String status,
                                              String currentConfig, String envId, int minutes, int units) {
        double[] cpu = metricStats(envId, appName, "cpu", minutes);   // {avg, max, count}
        double[] mem = metricStats(envId, appName, "memory", minutes);
        double avgCpu = cpu[0], maxCpu = cpu[1];
        long avgMem = (long) mem[0];

        String recommendation;
        String confidence;
        if (cpu[2] == 0) {
            recommendation = "No monitoring data — enable Anypoint Monitoring on this app to get a recommendation.";
            confidence = "NONE";
        } else if (maxCpu < UNDERUTILIZED_CPU) {
            confidence = cpu[2] >= 30 ? "MEDIUM" : "LOW";
            if (platform.equals("CH2") && units > 1) {
                recommendation = "Underutilized (max CPU " + fmt(maxCpu) + "%). Scale from " + units
                        + " to " + (units - 1) + " replica(s) and re-check.";
            } else {
                recommendation = "Underutilized (max CPU " + fmt(maxCpu) + "%). Downsize the worker size / replica count.";
            }
        } else if (maxCpu > HOT_CPU) {
            confidence = "MEDIUM";
            recommendation = "Hot (max CPU " + fmt(maxCpu) + "%). Do NOT downsize; consider scaling up for headroom.";
        } else {
            confidence = "LOW";
            recommendation = "Appropriately sized (max CPU " + fmt(maxCpu) + "%).";
        }

        return RightsizeRecommendation.builder()
                .appName(appName)
                .platform(platform)
                .status(status)
                .currentConfig(currentConfig)
                .avgCpuPct(round1(avgCpu))
                .maxCpuPct(round1(maxCpu))
                .avgMemoryBytes(avgMem)
                .recommendation(recommendation)
                .confidence(confidence)
                .build();
    }

    // Returns {avg, max, count}
    private double[] metricStats(String envId, String appName, String metric, int minutes) {
        try {
            MonitoringMetric m = monitoringClient.queryMetric(envId, appName, metric, minutes);
            if (m == null || m.getDataPoints() == null || m.getDataPoints().isEmpty()) {
                return new double[]{0, 0, 0};
            }
            double sum = 0, max = 0;
            int n = 0;
            for (MonitoringMetric.DataPoint p : m.getDataPoints()) {
                double v = p.getValue();
                sum += v;
                if (v > max) max = v;
                n++;
            }
            return new double[]{n > 0 ? sum / n : 0, max, n};
        } catch (Exception e) {
            return new double[]{0, 0, 0};
        }
    }

    private List<String> findIdleQueues(String envId, int windowHours) {
        List<String> idle = new ArrayList<>();
        List<String> regions;
        try {
            regions = mqClient.listRegions(envId);
        } catch (Exception e) {
            regions = List.of();
        }
        if (regions == null || regions.isEmpty()) regions = List.of("us-east-1");

        for (String region : regions) {
            List<MqDestination> destinations;
            try {
                destinations = mqClient.listDestinations(envId, region);
            } catch (Exception e) {
                continue;
            }
            // DLQs are expected to sit quiet; deleting one breaks its source queue's routing.
            Set<String> dlqIds = new HashSet<>();
            for (MqDestination d : destinations) {
                if (d.getDeadLetterQueueId() != null && !d.getDeadLetterQueueId().isBlank()) {
                    dlqIds.add(d.getDeadLetterQueueId());
                }
            }
            List<String> queueNames = new ArrayList<>();
            for (MqDestination d : destinations) {
                if (!"exchange".equalsIgnoreCase(d.getType()) && !dlqIds.contains(d.getName())) {
                    queueNames.add(d.getName());
                }
            }
            if (queueNames.isEmpty()) continue;
            try {
                for (MqQueueStats s : mqClient.getQueueStats(envId, region, queueNames, windowHours)) {
                    if (s.getMessagesReceived() == 0 && s.getMessagesSent() == 0) {
                        idle.add(s.getName() + " (" + region + ")");
                    }
                }
            } catch (Exception ignored) { }
        }
        return idle;
    }

    private boolean isRunning(String status) {
        if (status == null) return false;
        String s = status.toUpperCase();
        return s.equals("STARTED") || s.equals("RUNNING") || s.equals("APPLIED") || s.equals("DEPLOYED");
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }
}
