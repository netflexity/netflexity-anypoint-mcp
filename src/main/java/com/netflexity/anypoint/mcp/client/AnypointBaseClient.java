package com.netflexity.anypoint.mcp.client;

import com.netflexity.anypoint.mcp.context.AnypointContextHolder;
import com.netflexity.anypoint.mcp.context.AnypointRequestContext;
import com.netflexity.anypoint.mcp.context.TenantTokenCache;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public abstract class AnypointBaseClient {

    protected final WebClient webClient;
    protected final TenantTokenCache tokenCache;

    protected AnypointBaseClient(WebClient webClient, TenantTokenCache tokenCache) {
        this.webClient = webClient;
        this.tokenCache = tokenCache;
    }

    protected AnypointRequestContext context() {
        return AnypointContextHolder.require();
    }

    protected Mono<String> bearerToken(AnypointRequestContext ctx) {
        return tokenCache.getAuthClient(ctx).getAccessToken()
                .map(t -> "Bearer " + t.getAccessToken());
    }
}
