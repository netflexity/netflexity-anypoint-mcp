package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MqAuditEvent {
    private String id;
    private String action;
    private String objectName;
    private String objectType;
    private String userName;
    private String userEmail;
    private String createdAt;
    private String environmentName;
}
