package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MqDestination {
    private String name;
    private String type;
    private String region;
    private long messageCount;
    private long inFlight;
    private boolean fifo;
    private boolean encrypted;
    private String deadLetterQueueId;
}
