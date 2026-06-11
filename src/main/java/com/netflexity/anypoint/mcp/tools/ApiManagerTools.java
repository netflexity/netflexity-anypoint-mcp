package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.ApiManagerClient;
import com.netflexity.anypoint.mcp.client.EnvironmentClient;
import com.netflexity.anypoint.mcp.model.ManagedApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApiManagerTools {

    private final ApiManagerClient apiManagerClient;
    private final EnvironmentClient environmentClient;

    public ApiManagerTools(ApiManagerClient apiManagerClient,
                            EnvironmentClient environmentClient) {
        this.apiManagerClient = apiManagerClient;
        this.environmentClient = environmentClient;
    }

    @Tool(description = """
            List all APIs managed in Anypoint API Manager for a given environment.
            Returns each API's id, name, asset version, endpoint URI, status (Active/Inactive),
            and list of applied policy template IDs (e.g. "rate-limiting", "jwt-validation").
            Use environment name (e.g. "Production") or environment ID.
            """)
    public List<ManagedApi> listApis(String environment) {
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return apiManagerClient.listApis(envId);
    }
}
