package com.netflexity.anypoint.mcp.config;

import com.netflexity.anypoint.mcp.tools.*;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpToolsConfig {

    @Bean
    public ToolCallbackProvider anypointToolCallbacks(
            EnvironmentTools environmentTools,
            RuntimeManagerTools runtimeManagerTools,
            ApiManagerTools apiManagerTools,
            ExchangeTools exchangeTools,
            MqTools mqTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(environmentTools, runtimeManagerTools, apiManagerTools, exchangeTools, mqTools)
                .build();
    }
}
