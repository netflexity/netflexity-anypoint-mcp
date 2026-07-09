package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Triage summary for a single dead-letter queue: how deep it is, which source
 * queue feeds it, and the top failure signatures found by sampling its messages.
 */
@Data
@Builder
public class DlqTriageReport {
    private String dlqName;
    private String sourceQueue;
    private String region;
    private long messagesInQueue;
    private long messagesInFlight;
    private int sampled;
    private List<ErrorCluster> topErrors;
}
