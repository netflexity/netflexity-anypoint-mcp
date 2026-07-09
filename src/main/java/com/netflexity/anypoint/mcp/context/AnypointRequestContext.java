package com.netflexity.anypoint.mcp.context;

import org.springframework.http.HttpHeaders;

public class AnypointRequestContext {

    private final String clientId;
    private final String clientSecret;
    private final String orgId;
    private final String defaultEnv;
    private final String baseUrl;
    private final String apiKey;

    public AnypointRequestContext(String clientId, String clientSecret,
                                   String orgId, String defaultEnv, String baseUrl,
                                   String apiKey) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.orgId = orgId;
        this.defaultEnv = defaultEnv;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public static AnypointRequestContext fromHeaders(HttpHeaders headers) {
        return new AnypointRequestContext(
                headers.getFirst("X-Anypoint-Client-Id"),
                headers.getFirst("X-Anypoint-Client-Secret"),
                headers.getFirst("X-Anypoint-Org-Id"),
                defaultIfBlank(headers.getFirst("X-Anypoint-Env"), "Production"),
                defaultIfBlank(headers.getFirst("X-Anypoint-Base-Url"),
                        "https://anypoint.mulesoft.com"),
                headers.getFirst("X-API-Key")
        );
    }

    private static String defaultIfBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    public boolean isValid() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && orgId != null && !orgId.isBlank();
    }

    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getOrgId() { return orgId; }
    public String getDefaultEnv() { return defaultEnv; }
    public String getBaseUrl() { return baseUrl; }
    public String getApiKey() { return apiKey; }
}
