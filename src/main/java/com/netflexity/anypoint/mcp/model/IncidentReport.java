package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * A correlated incident diagnosis: a single time-ordered timeline stitched from
 * app logs, config audit events, monitoring alerts, and queue backpressure, plus
 * a ranked list of probable causes. Enterprise tier.
 */
@Data
@Builder
public class IncidentReport {
    private String environment;
    private String appName;
    private int lookbackHours;
    private List<TimelineEvent> timeline;
    private List<String> probableCauses;
    private String summary;
}
