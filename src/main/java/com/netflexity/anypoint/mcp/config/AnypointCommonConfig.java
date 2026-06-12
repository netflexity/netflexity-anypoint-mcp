package com.netflexity.anypoint.mcp.config;

import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.time.Instant;

@Configuration
public class AnypointCommonConfig {

    private static final Logger log = LoggerFactory.getLogger("anypoint.api");

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
                .filter(tracingFilter())
                .build();
    }

    private ExchangeFilterFunction tracingFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.info("--> {} {}", request.method(), redactUrl(request));
            return Mono.just(ClientRequest.from(request).build());
        }).andThen(ExchangeFilterFunction.ofResponseProcessor(response -> {
            // status only — body is streamed, can't read it here without buffering
            log.info("<-- {}", response.statusCode());
            return Mono.just(response);
        }));
    }

    private String redactUrl(ClientRequest request) {
        // Mask bearer token if accidentally in URL; keep everything else visible
        return request.url().toString().replaceAll("(?i)(token|secret|password)=[^&]+", "$1=***");
    }
}
