package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MqQueueDepth {
    private String name;
    private String region;
    private long messagesInQueue;
    private long messagesInFlight;
}
