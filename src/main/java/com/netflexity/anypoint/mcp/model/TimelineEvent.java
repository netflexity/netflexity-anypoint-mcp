package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

/**
 * A single correlated event on an incident timeline (a log error, a config change,
 * a firing alert, or queue backpressure).
 */
@Data
@Builder
public class TimelineEvent {
    private long timestamp;
    private String time;     // ISO-8601 for readability
    private String source;   // LOG | AUDIT | ALERT | QUEUE
    private String severity; // INFO | WARN | ERROR
    private String detail;
}
