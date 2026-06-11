package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.AnypointMqClient;
import com.netflexity.anypoint.mcp.client.EnvironmentClient;
import com.netflexity.anypoint.mcp.model.MqDestination;
import com.netflexity.anypoint.mcp.model.MqQueueConfig;
import com.netflexity.anypoint.mcp.model.MqQueueStats;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

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

    @Tool(description = """
            Get message throughput statistics for Anypoint MQ queues over a time window.
            Use this to find the most active queues, compare throughput, or identify idle queues.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - region: AWS region (e.g. "us-east-1"). Defaults to "us-east-1".
              - queueNames: list of queue names to get stats for (use listDestinations first to get names)
              - periodHours: how many hours back to look (1–168). Defaults to 1 hour.
            Returns per-queue counts of messagesReceived, messagesSent, and messagesVisible
            aggregated over the requested period. Sort by messagesReceived to find the most active queue.
            """)
    public List<MqQueueStats> getQueueStats(String environment, String region,
                                             List<String> queueNames, int periodHours) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return mqClient.getQueueStats(envId, region, queueNames, periodHours);
    }

    @Tool(description = """
            Send a message to an Anypoint MQ queue or message exchange.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - region: AWS region where MQ is deployed (e.g. "us-east-1"). Defaults to "us-east-1".
              - destination: queue or exchange name to send the message to
              - messageBody: the message payload (JSON string or plain text)
            Returns "Message sent" on success.
            """)
    public String sendMessage(String environment, String region, String destination,
                               String messageBody) {
        // TODO: licenseService.requirePro();
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return mqClient.sendMessage(envId, region, destination, messageBody);
    }

    @Tool(description = """
            Purge all messages from an Anypoint MQ queue.
            WARNING: This is a DESTRUCTIVE operation — all messages currently in the queue
            will be permanently deleted and cannot be recovered. Use with extreme caution.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - region: AWS region where MQ is deployed (e.g. "us-east-1"). Defaults to "us-east-1".
              - destination: queue name to purge
            Returns "Queue purged" on success.
            """)
    public String purgeQueue(String environment, String region, String destination) {
        // TODO: licenseService.requirePro();
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return mqClient.purgeQueue(envId, region, destination);
    }

    @Tool(description = """
            Create a new Anypoint MQ queue in a given environment and region.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - region: AWS region where MQ is deployed (e.g. "us-east-1"). Defaults to "us-east-1".
              - queueName: name for the new queue
              - fifo: whether to create a FIFO (ordered) queue; true for FIFO, false for standard
              - encrypted: whether to encrypt messages at rest
            Returns the created queue configuration including TTL settings and flags.
            """)
    public MqQueueConfig createQueue(String environment, String region, String queueName,
                                      boolean fifo, boolean encrypted) {
        // TODO: licenseService.requirePro();
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return mqClient.createQueue(envId, region, queueName, fifo, encrypted);
    }

    @Tool(description = """
            Delete an Anypoint MQ queue from a given environment and region.
            WARNING: This is a DESTRUCTIVE operation — the queue and all its messages
            will be permanently deleted. This action cannot be undone. Use with extreme caution.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - region: AWS region where MQ is deployed (e.g. "us-east-1"). Defaults to "us-east-1".
              - queueName: name of the queue to delete
            Returns "Queue deleted" on success.
            """)
    public String deleteQueue(String environment, String region, String queueName) {
        // TODO: licenseService.requirePro();
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return mqClient.deleteQueue(envId, region, queueName);
    }
}
