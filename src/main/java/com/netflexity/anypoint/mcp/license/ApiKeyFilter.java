package com.netflexity.anypoint.mcp.license;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class ApiKeyFilter implements WebFilter {

    private final LicenseService licenseService;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(LicenseService licenseService, ObjectMapper objectMapper) {
        this.licenseService = licenseService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-Key");

        try {
            licenseService.checkRateLimit(apiKey);
        } catch (RateLimitExceededException e) {
            return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        } catch (AccessDeniedException e) {
            return writeError(exchange, HttpStatus.FORBIDDEN, "Pro license required");
        }

        // Store resolved API key downstream via a mutated request header
        String resolvedKey = (apiKey != null) ? apiKey : "";
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(r -> r.header("X-Resolved-API-Key", resolvedKey))
                .build();

        return chain.filter(mutatedExchange);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("error", message));
        } catch (JsonProcessingException e) {
            body = "{\"error\":\"" + message + "\"}";
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
