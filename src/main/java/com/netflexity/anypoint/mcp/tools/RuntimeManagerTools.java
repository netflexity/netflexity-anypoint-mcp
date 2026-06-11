package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.EnvironmentClient;
import com.netflexity.anypoint.mcp.client.RuntimeManagerClient;
import com.netflexity.anypoint.mcp.model.MuleApp;
import com.netflexity.anypoint.mcp.model.MuleAppLog;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RuntimeManagerTools {

    private final RuntimeManagerClient runtimeManagerClient;
    private final EnvironmentClient environmentClient;

    public RuntimeManagerTools(RuntimeManagerClient runtimeManagerClient,
                                EnvironmentClient environmentClient) {
        this.runtimeManagerClient = runtimeManagerClient;
        this.environmentClient = environmentClient;
    }

    @Tool(description = """
            List all CloudHub 1.0 Mule applications in a given environment.
            Returns name, status (STARTED/STOPPED/FAILED), runtime version, worker size/count, region, and URL.
            Use environment name (e.g. "Production", "DEV") or environment ID.
            """)
    public List<MuleApp> listApplications(String environment) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return runtimeManagerClient.listApplications(envId);
    }

    @Tool(description = """
            Get details for a specific CloudHub 1.0 Mule application.
            Returns status, runtime version, worker configuration, region, and full domain URL.
            Use the app's domain name (e.g. "my-api-dev") not its display name.
            """)
    public MuleApp getApplication(String environment, String appName) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return runtimeManagerClient.getApplication(envId, appName);
    }

    @Tool(description = """
            Fetch recent log entries for a CloudHub 1.0 Mule application.
            Parameters:
              - environment: environment name or ID
              - appName: app domain name (e.g. "my-api-dev")
              - lines: number of log lines to return (default 100, max 500)
              - sinceMinutes: how far back to look (default 60, i.e. last hour)
            Returns timestamp, log level (INFO/WARN/ERROR), and message for each entry.
            """)
    public List<MuleAppLog> getApplicationLogs(String environment, String appName,
                                                int lines, int sinceMinutes) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        int effectiveLines = lines <= 0 ? 100 : Math.min(lines, 500);
        int effectiveMinutes = sinceMinutes <= 0 ? 60 : sinceMinutes;
        return runtimeManagerClient.getApplicationLogs(envId, appName, effectiveLines, effectiveMinutes);
    }

    @Tool(description = """
            Restart a CloudHub 1.0 Mule application.
            Use with caution — this triggers a live restart of the application.
            Parameters:
              - environment: environment name or ID
              - appName: app domain name (e.g. "my-api-dev")
            Returns a confirmation message when the restart is initiated.
            """)
    public String restartApplication(String environment, String appName) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return runtimeManagerClient.restartApplication(envId, appName);
    }
}
