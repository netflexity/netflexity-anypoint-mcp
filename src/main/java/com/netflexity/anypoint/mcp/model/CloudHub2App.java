package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CloudHub2App {
    private String id;
    private String name;
    private String status;
    private String target;       // deployment target (shared-space, private-space name)
    private String runtimeVersion;
    private int replicas;
    private String lastModified;
    private String applicationUrl;
}
