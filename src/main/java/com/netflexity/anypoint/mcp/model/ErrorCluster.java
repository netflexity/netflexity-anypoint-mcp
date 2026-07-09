package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

/**
 * A group of dead-letter messages sharing the same normalized failure signature.
 */
@Data
@Builder
public class ErrorCluster {
    private String signature;
    private long count;
    private String sampleMessageId;
    private String sampleBody;
}
