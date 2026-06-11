package com.netflexity.anypoint.mcp.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Shared WebClient — no baseUrl; each client builds absolute URIs from
 * the per-request X-Anypoint-Base-Url header (defaults to anypoint.mulesoft.com).
 * Per-tenant AnypointAuthClient instances live in TenantTokenCache.
 */
@Configuration
public class AnypointCommonConfig {

    @Bean
    public WebClient webClient() {
        ConnectionProvider cp = ConnectionProvider.builder("anypoint-mcp")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(cp)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000)
                .responseTimeout(Duration.ofSeconds(60));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }
}
