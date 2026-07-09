package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * A graded, at-a-glance health report for an Anypoint environment, built entirely
 * from read-only platform data. Free tier lead-magnet.
 */
@Data
@Builder
public class PlatformScorecard {
    private String environment;
    private String overallGrade; // A-F
    private int overallScore;     // 0-100
    private List<PillarGrade> pillars;
    private String summary;
}
