package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Cost and waste analysis for Anypoint MQ over a period. The dollar figure is
 * derived from a caller-supplied rate (dollarsPerBillableUnit) because MuleSoft
 * does not expose unit pricing via API. Never hardcode a price.
 */
@Data
@Builder
public class MqCostReport {
    private int periodDays;
    private long billableUnits;
    private double dollarsPerBillableUnit;
    private double estimatedCost;
    private long messageReceipts;
    private long messageBytes;
    private long avgMessageBytes;
    private long apiRequests;
    private int idleWindowHours;
    private List<String> idleQueues;
    private List<String> findings;
    private String summary;
}
