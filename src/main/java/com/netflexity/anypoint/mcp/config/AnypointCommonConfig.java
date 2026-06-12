package com.netflexity.anypoint.mcp.config;

import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

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
        return (request, next) -> {
            String url = request.url().toString()
                    .replaceAll("(?i)(token|secret|password)=[^&]+", "$1=***");
            log.info("--> {} {}", request.method(), url);
            return next.exchange(request)
                    .doOnNext(resp -> log.info("<-- {} {}", resp.statusCode().value(), url))
                    .doOnError(err -> log.warn("<-- ERROR {} {}", err.getMessage(), url));
        };
    }
}
