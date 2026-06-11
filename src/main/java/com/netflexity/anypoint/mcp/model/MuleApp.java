package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MuleApp {
    private String name;
    private String status;
    private String runtimeVersion;
    private String workers;
    private String region;
    private long lastUpdated;
    private String fullUrl;
}
