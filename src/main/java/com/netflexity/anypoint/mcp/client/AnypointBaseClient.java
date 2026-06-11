package com.netflexity.anypoint.mcp.client;

import com.netflexity.anypoint.common.client.AnypointAuthClient;
import com.netflexity.anypoint.mcp.config.AnypointMcpProperties;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Base class for all Anypoint API clients. Handles auth header injection
 * and org/env header attachment.
 */
public abstract class AnypointBaseClient {

    protected final WebClient webClient;
    protected final AnypointAuthClient authClient;
    protected final AnypointMcpProperties properties;

    protected AnypointBaseClient(WebClient webClient,
                                  AnypointAuthClient authClient,
                                  AnypointMcpProperties properties) {
        this.webClient = webClient;
        this.authClient = authClient;
        this.properties = properties;
    }

    protected Mono<String> bearerToken() {
        return authClient.getAccessToken().map(t -> "Bearer " + t.getAccessToken());
    }

    protected String orgId() {
        return properties.getOrganizationId();
    }
}
