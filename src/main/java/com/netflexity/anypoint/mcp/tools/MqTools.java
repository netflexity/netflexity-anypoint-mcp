package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.AnypointMqClient;
import com.netflexity.anypoint.mcp.client.EnvironmentClient;
import com.netflexity.anypoint.mcp.model.MqDestination;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MqTools {

    private final AnypointMqClient mqClient;
    private final EnvironmentClient environmentClient;

    public MqTools(AnypointMqClient mqClient, EnvironmentClient environmentClient) {
        this.mqClient = mqClient;
        this.environmentClient = environmentClient;
    }

    @Tool(description = """
            List all Anypoint MQ destinations (queues and exchanges) in a given environment and region.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - region: AWS region where MQ is deployed (e.g. "us-east-1", "eu-west-1").
                        Defaults to "us-east-1" if blank.
            Returns name, type (queue/exchange), region, message count, in-flight count,
            FIFO flag, encryption flag, and dead-letter queue ID.
            """)
    public List<MqDestination> listDestinations(String environment, String region) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return mqClient.listDestinations(envId, region);
    }

    @Tool(description = """
            List all available Anypoint MQ regions for a given environment.
            Returns region IDs (e.g. "us-east-1", "eu-west-1", "ap-southeast-1").
            Use this to discover valid region values before calling listDestinations.
            """)
    public List<String> listRegions(String environment) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return mqClient.listRegions(envId);
    }
}
