package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.*;
import com.netflexity.anypoint.mcp.context.AnypointContextHolder;
import com.netflexity.anypoint.mcp.license.LicenseService;
import com.netflexity.anypoint.mcp.model.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class InsightsTools {

    private static final long BACKPRESSURE_THRESHOLD = 1000L;

    private final EnvironmentClient environmentClient;
    private final RuntimeManagerClient runtimeManagerClient;
    private final CloudHub2Client cloudHub2Client;
    private final MonitoringClient monitoringClient;
    private final AnypointMqClient mqClient;
    private final LicenseService licenseService;

    public InsightsTools(EnvironmentClient environmentClient,
                         RuntimeManagerClient runtimeManagerClient,
                         CloudHub2Client cloudHub2Client,
                         MonitoringClient monitoringClient,
                         AnypointMqClient mqClient,
                         LicenseService licenseService) {
        this.environmentClient = environmentClient;
        this.runtimeManagerClient = runtimeManagerClient;
        this.cloudHub2Client = cloudHub2Client;
        this.monitoringClient = monitoringClient;
        this.mqClient = mqClient;
        this.licenseService = licenseService;
    }

    private String apiKey() {
        return AnypointContextHolder.require().getApiKey();
    }

    @Tool(description = """
            Grade the overall health of an Anypoint environment in one shot: a scorecard across
            Reliability (app status + alerts), DLQ Health (dead-letter backlog), Queue Backpressure,
            and Config Hygiene (recent config changes). Every finding names the tool that fixes it.
            Read-only and non-destructive. Great first call to see where an org stands.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
            Returns an overall A-F grade plus a per-pillar breakdown with findings and recommended fixes.
            Free tier.
            """)
    public PlatformScorecard getPlatformHealthScorecard(String environment) {
        Environment env = environmentClient.resolveEnvironment(environment);
        String envId = env.getId();
        String envName = env.getName() != null ? env.getName() : environment;

        List<PillarGrade> pillars = new ArrayList<>();
        pillars.add(gradeReliability(envId));
        DlqRollup dlq = rollupDlqs(envId);
        pillars.add(gradeDlqHealth(dlq));
        pillars.add(gradeBackpressure(dlq));
        pillars.add(gradeConfigHygiene(envName));

        // Score only pillars where data was actually available (score >= 0). A pillar whose
        // source API was unreachable is graded N/A so an outage cannot masquerade as grade A.
        List<PillarGrade> graded = pillars.stream().filter(p -> p.getScore() >= 0).toList();
        boolean anyData = !graded.isEmpty();
        int avg = anyData
                ? (int) Math.round(graded.stream().mapToInt(PillarGrade::getScore).average().orElse(0))
                : 0;
        String overall = anyData ? letter(avg) : "N/A";
        PillarGrade worst = graded.stream().min(Comparator.comparingInt(PillarGrade::getScore)).orElse(null);

        long unavailable = pillars.stream().filter(p -> p.getScore() < 0).count();
        String summary;
        if (!anyData) {
            summary = "Could not read any platform data for " + envName + " — check credentials/connectivity.";
        } else {
            summary = "Overall " + overall + (worst != null ? ". Weakest area: " + worst.getPillar()
                    + " (" + worst.getGrade() + ")" : "")
                    + (unavailable > 0 ? ". " + unavailable + " pillar(s) N/A (data unavailable)." : ".");
        }

        return PlatformScorecard.builder()
                .environment(envName)
                .overallGrade(overall)
                .overallScore(avg)
                .pillars(pillars)
                .summary(summary)
                .build();
    }

    @Tool(description = """
            Diagnose an incident for a specific CloudHub application by stitching a single
            time-ordered timeline from app logs (errors/warnings), MQ config audit events,
            active monitoring alerts, and queue backpressure, then ranking probable causes.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - appName: CloudHub 1.0 app domain name (e.g. "orders-api-prod")
              - lookbackHours: how far back to correlate (1-72, default 6)
            Returns a merged timeline and a ranked probable-cause list. Enterprise tier.
            """)
    public IncidentReport diagnoseIncident(String environment, String appName, int lookbackHours) {
        licenseService.requireEnterprise(apiKey());
        Environment env = environmentClient.resolveEnvironment(environment);
        String envId = env.getId();
        String envName = env.getName() != null ? env.getName() : environment;
        int hours = (lookbackHours > 0 && lookbackHours <= 72) ? lookbackHours : 6;

        List<TimelineEvent> timeline = new ArrayList<>();
        int errorCount = 0;

        // 1) App logs
        try {
            List<MuleAppLog> logs = runtimeManagerClient.getApplicationLogs(envId, appName, 200, hours * 60);
            for (MuleAppLog l : logs) {
                String level = l.getLevel() == null ? "" : l.getLevel().toUpperCase();
                if (level.equals("ERROR") || level.equals("WARN")) {
                    if (level.equals("ERROR")) errorCount++;
                    timeline.add(TimelineEvent.builder()
                            .timestamp(l.getTimestamp())
                            .time(iso(l.getTimestamp()))
                            .source("LOG")
                            .severity(level)
                            .detail(truncate(l.getMessage(), 240))
                            .build());
                }
            }
        } catch (Exception ignored) { }

        // 2) MQ config audit events
        int configChanges = 0;
        try {
            for (MqAuditEvent e : mqClient.getMqAuditLog(hours)) {
                if (!inEnvironment(e, envName)) continue; // audit is org-wide; scope to this env
                long ts = parseIso(e.getCreatedAt());
                String action = e.getAction() == null ? "" : e.getAction();
                if (action.equalsIgnoreCase("Delete") || action.equalsIgnoreCase("Update")) configChanges++;
                timeline.add(TimelineEvent.builder()
                        .timestamp(ts)
                        .time(e.getCreatedAt())
                        .source("AUDIT")
                        .severity(action.equalsIgnoreCase("Delete") ? "WARN" : "INFO")
                        .detail(action + " " + e.getObjectType() + " '" + e.getObjectName() + "' by " + e.getUserName())
                        .build());
            }
        } catch (Exception ignored) { }

        // 3) Active alerts (raw JSON snapshot)
        boolean alertsPresent = false;
        try {
            String alerts = monitoringClient.listAlerts(envId);
            if (alerts != null && !alerts.isBlank() && !alerts.trim().equals("[]")) {
                alertsPresent = true;
                timeline.add(TimelineEvent.builder()
                        .timestamp(System.currentTimeMillis())
                        .time(iso(System.currentTimeMillis()))
                        .source("ALERT")
                        .severity("WARN")
                        .detail("Active monitoring alerts: " + truncate(alerts, 300))
                        .build());
            }
        } catch (Exception ignored) { }

        // 4) Queue backpressure
        List<String> deepQueues = new ArrayList<>();
        try {
            for (MqQueueDepth d : rollupDlqs(envId).allDepths) {
                if (d.getMessagesInQueue() >= BACKPRESSURE_THRESHOLD) {
                    deepQueues.add(d.getName() + " (" + d.getMessagesInQueue() + ")");
                    timeline.add(TimelineEvent.builder()
                            .timestamp(System.currentTimeMillis())
                            .time(iso(System.currentTimeMillis()))
                            .source("QUEUE")
                            .severity("WARN")
                            .detail("Backpressure: queue " + d.getName() + " has " + d.getMessagesInQueue() + " messages waiting")
                            .build());
                }
            }
        } catch (Exception ignored) { }

        timeline.sort(Comparator.comparingLong(TimelineEvent::getTimestamp));

        List<String> causes = new ArrayList<>();
        if (configChanges > 0 && errorCount > 0) {
            causes.add(configChanges + " MQ config change(s) in the window overlap with " + errorCount
                    + " error log(s) — a recent change is a likely trigger.");
        }
        if (!deepQueues.isEmpty()) {
            causes.add("Queue backpressure on " + deepQueues.size() + " queue(s): " + String.join(", ", deepQueues)
                    + " — a downstream consumer may be slow or stopped.");
        }
        if (alertsPresent) causes.add("Active monitoring alerts are firing for this environment.");
        if (errorCount >= 20) causes.add("Elevated error rate: " + errorCount + " error log lines in the last " + hours + "h.");
        if (causes.isEmpty()) causes.add("No strongly correlated cause found in the window; widen lookbackHours or check upstream dependencies.");

        String summary = "Correlated " + timeline.size() + " event(s) over " + hours + "h for " + appName
                + ": " + errorCount + " error log(s), " + configChanges + " config change(s), "
                + deepQueues.size() + " backed-up queue(s)" + (alertsPresent ? ", alerts firing." : ".");

        return IncidentReport.builder()
                .environment(environment)
                .appName(appName)
                .lookbackHours(hours)
                .timeline(timeline)
                .probableCauses(causes)
                .summary(summary)
                .build();
    }

    // ---- Reliability ----
    private PillarGrade gradeReliability(String envId) {
        List<String> findings = new ArrayList<>();
        int total = 0, unhealthy = 0;
        boolean ch1Ok = false, ch2Ok = false;

        try {
            for (MuleApp a : runtimeManagerClient.listApplications(envId)) {
                total++;
                if (!isHealthy(a.getStatus())) {
                    unhealthy++;
                    findings.add("CH1 app '" + a.getName() + "' is " + a.getStatus());
                }
            }
            ch1Ok = true;
        } catch (Exception ignored) { }
        try {
            for (CloudHub2App a : cloudHub2Client.listApplications(envId)) {
                total++;
                if (!isHealthy(a.getStatus())) {
                    unhealthy++;
                    findings.add("CH2 app '" + a.getName() + "' is " + a.getStatus());
                }
            }
            ch2Ok = true;
        } catch (Exception ignored) { }

        // If neither app inventory could be read, grade N/A rather than a false "all healthy".
        if (!ch1Ok && !ch2Ok) {
            return naPillar("Reliability",
                    "App inventory unavailable (Runtime Manager and CloudHub 2.0 both unreachable).",
                    "diagnoseIncident (Enterprise) for RCA; restartApplication / restartCh2Application (Pro) to remediate");
        }

        try {
            String alerts = monitoringClient.listAlerts(envId);
            if (alerts != null && !alerts.isBlank() && !alerts.trim().equals("[]")) {
                findings.add("Monitoring alerts are active — run diagnoseIncident for root cause.");
            }
        } catch (Exception ignored) { }

        int score = total == 0 ? 100 : (int) Math.round(100.0 * (total - unhealthy) / total);
        if (findings.isEmpty()) findings.add(total == 0 ? "No apps found in this environment." : "All " + total + " app(s) healthy.");
        return PillarGrade.builder()
                .pillar("Reliability")
                .grade(letter(score))
                .score(score)
                .findings(findings)
                .fixWith("diagnoseIncident (Enterprise) for RCA; restartApplication / restartCh2Application (Pro) to remediate")
                .build();
    }

    private PillarGrade naPillar(String pillar, String finding, String fixWith) {
        return PillarGrade.builder()
                .pillar(pillar)
                .grade("N/A")
                .score(-1)
                .findings(List.of(finding))
                .fixWith(fixWith)
                .build();
    }

    // ---- DLQ health ----
    private PillarGrade gradeDlqHealth(DlqRollup dlq) {
        if (dlq.mqUnreachable) {
            return naPillar("DLQ Health", "MQ data unavailable — could not read destinations in any region.",
                    "triageDeadLetterQueues (Free) to see failures; redriveMessages (Pro) to replay them");
        }
        List<String> findings = new ArrayList<>();
        int score;
        if (dlq.totalDeadLetters == 0) {
            score = 100;
            findings.add("No dead-letter backlog.");
        } else {
            long d = dlq.totalDeadLetters;
            score = d <= 10 ? 80 : d <= 100 ? 70 : d <= 1000 ? 60 : 40;
            findings.add(dlq.dlqsWithMessages + " DLQ(s) holding " + d + " dead-letter message(s).");
        }
        return PillarGrade.builder()
                .pillar("DLQ Health")
                .grade(letter(score))
                .score(score)
                .findings(findings)
                .fixWith("triageDeadLetterQueues (Free) to see failures; redriveMessages (Pro) to replay them")
                .build();
    }

    // ---- Backpressure ----
    private PillarGrade gradeBackpressure(DlqRollup dlq) {
        if (dlq.mqUnreachable) {
            return naPillar("Queue Backpressure", "MQ data unavailable — could not read queue depths.",
                    "rightsizeWorkers (Pro) / scaleWorkers (Pro) to add consumer capacity");
        }
        List<String> findings = new ArrayList<>();
        int deep = 0;
        for (MqQueueDepth d : dlq.allDepths) {
            if (d.getMessagesInQueue() >= BACKPRESSURE_THRESHOLD) {
                deep++;
                findings.add("Queue '" + d.getName() + "' has " + d.getMessagesInQueue() + " messages waiting.");
            }
        }
        int score = deep == 0 ? 100 : deep == 1 ? 75 : deep <= 3 ? 60 : 40;
        if (findings.isEmpty()) findings.add("No queues above the " + BACKPRESSURE_THRESHOLD + "-message backpressure threshold.");
        return PillarGrade.builder()
                .pillar("Queue Backpressure")
                .grade(letter(score))
                .score(score)
                .findings(findings)
                .fixWith("rightsizeWorkers (Pro) / scaleWorkers (Pro) to add consumer capacity")
                .build();
    }

    // ---- Config hygiene ----
    private PillarGrade gradeConfigHygiene(String envName) {
        List<String> findings = new ArrayList<>();
        int deletes = 0, updates = 0;
        boolean ok = false;
        try {
            // getMqAuditLog is org-wide; scope to this environment by name to avoid mixing envs.
            for (MqAuditEvent e : mqClient.getMqAuditLog(24)) {
                if (!inEnvironment(e, envName)) continue;
                String a = e.getAction() == null ? "" : e.getAction();
                if (a.equalsIgnoreCase("Delete")) deletes++;
                else if (a.equalsIgnoreCase("Update")) updates++;
            }
            ok = true;
        } catch (Exception ignored) { }
        if (!ok) {
            return naPillar("Config Hygiene", "MQ audit log unavailable.",
                    "getMqAuditLog for detail; config drift detection (Pro, roadmap)");
        }
        int score = deletes == 0 ? (updates <= 5 ? 100 : 85) : deletes <= 2 ? 75 : 55;
        if (deletes > 0) findings.add(deletes + " destination delete(s) in the last 24h — confirm these were intentional.");
        if (updates > 0) findings.add(updates + " config update(s) in the last 24h.");
        if (findings.isEmpty()) findings.add("No MQ config changes in the last 24h.");
        return PillarGrade.builder()
                .pillar("Config Hygiene")
                .grade(letter(score))
                .score(score)
                .findings(findings)
                .fixWith("getMqAuditLog for detail; config drift detection (Pro, roadmap)")
                .build();
    }

    // ---- shared MQ rollup across all regions ----
    private DlqRollup rollupDlqs(String envId) {
        DlqRollup r = new DlqRollup();
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
            r.regionsRead++;
            Set<String> dlqNames = new LinkedHashSet<>();
            for (MqDestination d : destinations) {
                if (d.getDeadLetterQueueId() != null && !d.getDeadLetterQueueId().isBlank()) {
                    dlqNames.add(d.getDeadLetterQueueId());
                }
            }
            Set<String> existing = new HashSet<>();
            List<String> queueNames = new ArrayList<>();
            for (MqDestination d : destinations) {
                existing.add(d.getName());
                // Only queues have depth; passing an exchange id can 400 the whole batch.
                if (!"exchange".equalsIgnoreCase(d.getType())) queueNames.add(d.getName());
            }
            // Depths for every queue (for backpressure) — batched by the client call.
            if (!queueNames.isEmpty()) {
                try {
                    r.allDepths.addAll(mqClient.getQueueDepth(envId, region, queueNames));
                } catch (Exception ignored) { }
            }
            // Dead-letter totals from the DLQ subset.
            List<String> liveDlqs = new ArrayList<>();
            for (String n : dlqNames) if (existing.contains(n)) liveDlqs.add(n);
            if (!liveDlqs.isEmpty()) {
                try {
                    for (MqQueueDepth depth : mqClient.getQueueDepth(envId, region, liveDlqs)) {
                        if (depth.getMessagesInQueue() > 0) {
                            r.dlqsWithMessages++;
                            r.totalDeadLetters += depth.getMessagesInQueue();
                        }
                    }
                } catch (Exception ignored) { }
            }
        }
        r.mqUnreachable = r.regionsRead == 0;
        return r;
    }

    private static class DlqRollup {
        int dlqsWithMessages = 0;
        long totalDeadLetters = 0;
        int regionsRead = 0;
        boolean mqUnreachable = false;
        List<MqQueueDepth> allDepths = new ArrayList<>();
    }

    private boolean isHealthy(String status) {
        if (status == null) return false;
        String s = status.toUpperCase();
        return s.equals("STARTED") || s.equals("RUNNING") || s.equals("APPLIED") || s.equals("DEPLOYED");
    }

    private String letter(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 60) return "D";
        return "F";
    }

    private String iso(long epochMs) {
        try {
            return Instant.ofEpochMilli(epochMs).toString();
        } catch (Exception e) {
            return String.valueOf(epochMs);
        }
    }

    private long parseIso(String iso) {
        // Fall back to "now" (not 0) so an unparseable timestamp does not sort to the top of the timeline.
        if (iso == null || iso.isBlank()) return System.currentTimeMillis();
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    // Best-effort scope of an org-wide audit event to a single environment by name.
    private boolean inEnvironment(MqAuditEvent e, String envName) {
        if (envName == null || envName.isBlank()) return true;
        String en = e.getEnvironmentName();
        return en == null || en.isBlank() || en.equalsIgnoreCase(envName);
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }
}
