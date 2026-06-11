package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.CloudHub2Client;
import com.netflexity.anypoint.mcp.client.EnvironmentClient;
import com.netflexity.anypoint.mcp.model.CloudHub2App;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CloudHub2Tools {

    private final CloudHub2Client cloudHub2Client;
    private final EnvironmentClient environmentClient;

    public CloudHub2Tools(CloudHub2Client cloudHub2Client,
                           EnvironmentClient environmentClient) {
        this.cloudHub2Client = cloudHub2Client;
        this.environmentClient = environmentClient;
    }

    @Tool(description = """
            List all CloudHub 2.0 / Runtime Fabric Mule applications in an environment.
            Returns id, name, status, deployment target (shared-space or private-space name),
            runtime version, replica count, last modified date.
            Use environment name (e.g. "Production") or environment ID.
            Note: this covers CH2/RTF deployments only — use listApplications for CloudHub 1.0.
            """)
    public List<CloudHub2App> listCh2Applications(String environment) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return cloudHub2Client.listApplications(envId);
    }

    @Tool(description = """
            Get details for a specific CloudHub 2.0 / Runtime Fabric deployment.
            Parameters:
              - environment: environment name or ID
              - deploymentId: the deployment UUID (get from listCh2Applications)
            Returns status, target, replicas, runtime version, last modified date.
            """)
    public CloudHub2App getCh2Application(String environment, String deploymentId) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return cloudHub2Client.getApplication(envId, deploymentId);
    }

    @Tool(description = """
            Scale a CloudHub 2.0 / Runtime Fabric deployment to a specific number of replicas.
            Parameters:
              - environment: environment name or ID
              - deploymentId: the deployment UUID (get from listCh2Applications)
              - replicas: desired replica count (1-8 for shared space, up to 16 for private)
            PRO LICENSE REQUIRED — write operation.
            """)
    public String scaleCh2Replicas(String environment, String deploymentId, int replicas) {
        // TODO: licenseService.requirePro();
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return cloudHub2Client.scaleReplicas(envId, deploymentId, replicas);
    }

    @Tool(description = """
            Restart a CloudHub 2.0 / Runtime Fabric application.
            Parameters:
              - environment: environment name or ID
              - deploymentId: the deployment UUID (get from listCh2Applications)
            Triggers a rolling restart of all replicas.
            PRO LICENSE REQUIRED — write operation.
            """)
    public String restartCh2Application(String environment, String deploymentId) {
        // TODO: licenseService.requirePro();
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return cloudHub2Client.restartApplication(envId, deploymentId);
    }
}
