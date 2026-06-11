package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MqMessage {
    private String messageId;
    private String lockId;
    private String contentType;
    private String body;
    private java.util.Map<String, Object> properties;
}
