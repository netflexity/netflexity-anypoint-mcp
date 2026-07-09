package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * One graded dimension of a platform health scorecard. Each finding names the
 * paid tool that fixes it, so a free scorecard doubles as an upgrade prompt.
 */
@Data
@Builder
public class PillarGrade {
    private String pillar;
    private String grade;   // A-F
    private int score;      // 0-100
    private List<String> findings;
    private String fixWith; // the Pro/Enterprise tool(s) that address this pillar
}
