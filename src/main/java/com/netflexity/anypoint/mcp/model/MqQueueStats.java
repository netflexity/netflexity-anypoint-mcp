package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MqQueueStats {
    private String name;
    private String region;
    private long messagesReceived;
    private long messagesSent;
    private long messagesVisible;
}
