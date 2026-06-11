package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MqUsageSummary {
    private int periodDays;
    private long totalMessageReceipts;
    private long totalBillableUnits;
    private long totalApiRequests;
    private long totalMessageBytes;
}
