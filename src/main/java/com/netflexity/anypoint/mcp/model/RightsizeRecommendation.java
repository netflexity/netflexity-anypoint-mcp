package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

/**
 * A per-application rightsizing recommendation derived from provisioned capacity
 * vs. observed CPU/memory utilization from Anypoint Monitoring.
 */
@Data
@Builder
public class RightsizeRecommendation {
    private String appName;
    private String platform;     // CH1 | CH2
    private String status;
    private String currentConfig;
    private double avgCpuPct;
    private double maxCpuPct;
    private long avgMemoryBytes;
    private String recommendation;
    private String confidence;    // NONE | LOW | MEDIUM
}
