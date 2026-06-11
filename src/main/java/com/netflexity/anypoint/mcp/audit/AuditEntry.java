package com.netflexity.anypoint.mcp.audit;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class AuditEntry {
    private Instant timestamp;
    private String apiKey;
    private String tool;
    private String environment;
    private String resource;
    private String action;
    private String result;  // "OK" or error message
    private long durationMs;
}
