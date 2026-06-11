package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Environment {
    private String id;
    private String name;
    private String type;
    private boolean isProduction;
}
