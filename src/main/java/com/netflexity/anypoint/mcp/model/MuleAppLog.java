package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MuleAppLog {
    private long timestamp;
    private String level;
    private String message;
}
