package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.ApiManagerClient;
import com.netflexity.anypoint.mcp.client.EnvironmentClient;
import com.netflexity.anypoint.mcp.model.ManagedApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

    @Tool(description = """
            Apply a policy to an API in Anypoint API Manager.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - apiId: the numeric API instance ID (from listApis)
              - policyTemplateId: the policy template identifier (e.g. "rate-limiting",
                                   "jwt-validation", "ip-allowlist")
              - configurationData: policy configuration key-value pairs (policy-specific);
                                    pass an empty map for policies with no required configuration
            Returns the raw JSON response from API Manager, which includes the policy instance ID.
            """)
    public String applyPolicy(String environment, String apiId, String policyTemplateId,
                               @ToolParam(description = "Policy configuration parameters as key-value pairs")
                               Map<String, Object> configurationData) {
        // TODO: licenseService.requirePro();
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return apiManagerClient.applyPolicy(envId, apiId, policyTemplateId, configurationData);
    }

    @Tool(description = """
            Remove an applied policy from an API in Anypoint API Manager.
            Parameters:
              - environment: environment name (e.g. "Production") or environment ID
              - apiId: the numeric API instance ID (from listApis)
              - policyId: the policy instance ID to remove (returned by applyPolicy)
            Returns "Policy removed" on success.
            """)
    public String removePolicy(String environment, String apiId, String policyId) {
        // TODO: licenseService.requirePro();
        String envId = environmentClient.resolveEnvironment(environment).getId();
        return apiManagerClient.removePolicy(envId, apiId, policyId);
    }
}
