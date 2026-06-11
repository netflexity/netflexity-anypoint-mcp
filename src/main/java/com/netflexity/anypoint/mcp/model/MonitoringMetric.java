package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MonitoringMetric {
    private String metricName;
    private String appName;
    private String environment;
    private List<DataPoint> dataPoints;

    @Data
    @Builder
    public static class DataPoint {
        private long timestamp;
        private double value;
    }
}
