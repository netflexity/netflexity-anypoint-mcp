package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MqQueueConfig {
    private String name;
    private String region;
    private long defaultTtl;
    private long defaultLockTtl;
    private boolean fifo;
    private boolean encrypted;
}
