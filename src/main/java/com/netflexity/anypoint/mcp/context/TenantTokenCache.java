package com.netflexity.anypoint.mcp.context;

import com.netflexity.anypoint.common.client.AnypointAuthClient;
import com.netflexity.anypoint.common.config.AnypointConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and caches one AnypointAuthClient per clientId.
 * Each client manages its own OAuth token independently.
 */
@Component
public class TenantTokenCache {

    private final WebClient webClient;
    private final ConcurrentHashMap<String, AnypointAuthClient> cache = new ConcurrentHashMap<>();

    public TenantTokenCache(WebClient webClient) {
        this.webClient = webClient;
    }

    public AnypointAuthClient getAuthClient(AnypointRequestContext ctx) {
        return cache.computeIfAbsent(ctx.getClientId(), k -> {
            AnypointConfig config = new AnypointConfig();
            config.setBaseUrl(ctx.getBaseUrl());
            config.getAuth().setClientId(ctx.getClientId());
            config.getAuth().setClientSecret(ctx.getClientSecret());
            return new AnypointAuthClient(webClient, config);
        });
    }
}
