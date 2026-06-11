package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.EnvironmentClient;
import com.netflexity.anypoint.mcp.model.Environment;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EnvironmentTools {

    private final EnvironmentClient environmentClient;

    public EnvironmentTools(EnvironmentClient environmentClient) {
        this.environmentClient = environmentClient;
    }

    @Tool(description = """
            List all Anypoint Platform environments (e.g. DEV, QA, PROD) for the configured organization.
            Returns each environment's id, name, type (production/sandbox/design), and isProduction flag.
            Use this to discover environment IDs needed by other tools.
            """)
    public List<Environment> listEnvironments() {
        return environmentClient.listEnvironments();
    }

    @Tool(description = """
            Resolve an environment by name (case-insensitive) or exact ID.
            Returns the matching Environment object with its id, name, type, and isProduction flag.
            Throws an error if the environment is not found.
            Examples: "Production", "DEV", "a1b2c3d4-..."
            """)
    public Environment resolveEnvironment(String nameOrId) {
        return environmentClient.resolveEnvironment(nameOrId);
    }
}
