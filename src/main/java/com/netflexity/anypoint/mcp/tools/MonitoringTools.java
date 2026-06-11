package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.EnvironmentClient;
import com.netflexity.anypoint.mcp.client.MonitoringClient;
import com.netflexity.anypoint.mcp.model.MonitoringMetric;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class MonitoringTools {

    private final MonitoringClient monitoringClient;
    private final EnvironmentClient environmentClient;

    public MonitoringTools(MonitoringClient monitoringClient,
                            EnvironmentClient environmentClient) {
        this.monitoringClient = monitoringClient;
        this.environmentClient = environmentClient;
    }

    @Tool(description = """
            Query a time-series metric for a CloudHub application over the last N minutes.
            Parameters:
              - environment: environment name or ID
              - appName: CloudHub app domain name (e.g. "my-api-dev")
              - metric: one of:
                  cpu            — CPU utilization (0-100%)
                  memory         — JVM heap used (bytes)
                  heap           — heap committed (bytes)
                  message-count  — messages processed per minute
                  error-rate     — errors per minute
                  response-time  — avg response time (ms)
              - lastMinutes: how far back to query (default 60, i.e. last hour)
            Returns a list of {timestamp, value} data points at 1-minute resolution.
            Requires Anypoint Monitoring to be enabled on the target app/environment.
            """)
    public MonitoringMetric queryMetric(String environment, String appName,
                                         String metric, int lastMinutes) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        int minutes = lastMinutes <= 0 ? 60 : Math.min(lastMinutes, 1440);
        return monitoringClient.queryMetric(envId, appName, metric, minutes);
    }

    @Tool(description = """
            List active Anypoint Monitoring alerts for an environment.
            Returns raw alert JSON including alert name, condition, threshold, and current state.
            Use environment name (e.g. "Production") or environment ID.
            """)
    public String listAlerts(String environment) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return monitoringClient.listAlerts(envId);
    }
}
