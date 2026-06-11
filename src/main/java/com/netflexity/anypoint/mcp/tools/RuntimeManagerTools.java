package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.EnvironmentClient;
import com.netflexity.anypoint.mcp.client.RuntimeManagerClient;
import com.netflexity.anypoint.mcp.model.MuleApp;
import com.netflexity.anypoint.mcp.model.MuleAppLog;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

    @Tool(description = """
            Deploy or update a CloudHub 1.0 Mule application with full configuration.
            Updates the runtime version, worker count, worker size, and environment variables.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - appName: app domain name (e.g. "my-api-dev")
              - runtimeVersion: Mule runtime version (e.g. "4.6.0")
              - workerCount: number of workers (e.g. 1, 2, 4)
              - workerSize: worker size name (e.g. "Micro", "Small", "Medium", "Large", "xLarge",
                            "2xLarge", "4xLarge")
              - properties: map of application properties / environment variables to set
            Returns the updated application details.
            """)
    public MuleApp deployApplication(String environment, String appName,
                                      String runtimeVersion, int workerCount, String workerSize,
                                      @ToolParam(description = "Application properties / environment variables as key-value pairs")
                                      Map<String, String> properties) {
        // TODO: licenseService.requirePro();
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return runtimeManagerClient.deployApplication(envId, appName, runtimeVersion,
                workerCount, workerSize, properties);
    }

    @Tool(description = """
            Scale the workers of a CloudHub 1.0 Mule application.
            Changes the number and/or size of workers without altering other settings.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - appName: app domain name (e.g. "my-api-dev")
              - workerCount: new number of workers (e.g. 1, 2, 4)
              - workerSize: worker size name (e.g. "Micro", "Small", "Medium", "Large", "xLarge")
            Returns the updated application details.
            """)
    public MuleApp scaleWorkers(String environment, String appName,
                                 int workerCount, String workerSize) {
        // TODO: licenseService.requirePro();
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return runtimeManagerClient.scaleWorkers(envId, appName, workerCount, workerSize);
    }

    @Tool(description = """
            Set (overwrite) environment variables for a CloudHub 1.0 Mule application.
            In CloudHub 1, app-level properties serve as environment variables.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - appName: app domain name (e.g. "my-api-dev")
              - variables: map of variable names to values to apply to the application
            Returns the updated application details.
            """)
    public MuleApp setEnvironmentVariables(String environment, String appName,
                                            @ToolParam(description = "Environment variable key-value pairs to set on the application")
                                            Map<String, String> variables) {
        // TODO: licenseService.requirePro();
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return runtimeManagerClient.setEnvironmentVariables(envId, appName, variables);
    }
}
