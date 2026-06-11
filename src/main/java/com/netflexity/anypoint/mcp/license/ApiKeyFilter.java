package com.netflexity.anypoint.mcp.license;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflexity.anypoint.mcp.context.AnypointContextHolder;
import com.netflexity.anypoint.mcp.context.AnypointRequestContext;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
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
        HttpHeaders headers = exchange.getRequest().getHeaders();
        String apiKey = headers.getFirst("X-API-Key");

        try {
            licenseService.checkRateLimit(apiKey);
        } catch (RateLimitExceededException e) {
            return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded");
        } catch (AccessDeniedException e) {
            return writeError(exchange, HttpStatus.FORBIDDEN, "Pro license required");
        }

        AnypointRequestContext anypointCtx = AnypointRequestContext.fromHeaders(headers);

        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/mcp") && !anypointCtx.isValid()) {
            return writeError(exchange, HttpStatus.BAD_REQUEST,
                    "Missing required headers: X-Anypoint-Client-Id, X-Anypoint-Client-Secret, X-Anypoint-Org-Id");
        }

        AnypointContextHolder.set(anypointCtx);

        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(AnypointRequestContext.class, anypointCtx))
                .doFinally(s -> AnypointContextHolder.clear());
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
