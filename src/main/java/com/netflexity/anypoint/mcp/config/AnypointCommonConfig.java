package com.netflexity.anypoint.mcp.config;

import com.netflexity.anypoint.common.client.AnypointAuthClient;
import com.netflexity.anypoint.common.config.AnypointConfig;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * Imports only the beans we need from anypoint-common, bypassing ExporterConfig
 * (which pulls in Micrometer metrics and a conflicting WebClient bean).
 */
@Configuration
@Import(AnypointConfig.class)
public class AnypointCommonConfig {

    @Bean
    public WebClient webClient(AnypointConfig config) {
        ConnectionProvider cp = ConnectionProvider.builder("anypoint-mcp")
                .maxConnections(20)
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(cp)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        config.getHttp().getConnectTimeoutSeconds() * 1000)
                .responseTimeout(Duration.ofSeconds(config.getHttp().getReadTimeoutSeconds()))
                .baseUrl(config.getBaseUrl());

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(4 * 1024 * 1024))
                .build();
    }

    @Bean
    public AnypointAuthClient anypointAuthClient(WebClient webClient, AnypointConfig config) {
        return new AnypointAuthClient(webClient, config);
    }
}
