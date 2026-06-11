package com.netflexity.anypoint.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP-server-specific config. Auth and baseUrl are handled by AnypointConfig
 * from anypoint-common (anypoint.auth.*, anypoint.base-url).
 * This bean owns MCP-only fields: organizationId and defaultEnvironment.
 */
@Component
@ConfigurationProperties(prefix = "anypoint")
public class AnypointMcpProperties {

    private String organizationId;
    private String defaultEnvironment = "Production";

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getDefaultEnvironment() { return defaultEnvironment; }
    public void setDefaultEnvironment(String defaultEnvironment) { this.defaultEnvironment = defaultEnvironment; }
}
