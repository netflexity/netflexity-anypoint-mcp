package com.netflexity.anypoint.mcp.tools;

import com.netflexity.anypoint.mcp.client.ExchangeClient;
import com.netflexity.anypoint.mcp.model.ExchangeAsset;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExchangeTools {

    private final ExchangeClient exchangeClient;

    public ExchangeTools(ExchangeClient exchangeClient) {
        this.exchangeClient = exchangeClient;
    }

    @Tool(description = """
            Search Anypoint Exchange for published assets (APIs, connectors, templates, examples, policies).
            Parameters:
              - query: search term (asset name, keyword, or empty string for all)
              - types: optional comma-separated asset types to filter by.
                       Valid types: rest-api, soap-api, raml-fragment, http-api, mule-application,
                                    connector, template, example, policy, custom
                       Leave blank to search all types.
            Returns groupId, assetId, version, name, type, description, and creation date.
            Results are cached per query+types combination.
            """)
    public List<ExchangeAsset> searchAssets(String query, String types) {
        return exchangeClient.searchAssets(query, types);
    }

    @Tool(description = """
            Retrieve the API specification (RAML, OAS, WSDL) for a specific Exchange asset version.
            Parameters:
              - groupId: the asset's group/organization ID
              - assetId: the asset identifier
              - version: the semantic version (e.g. "1.0.0")
            Returns the raw specification text.
            Use searchAssets first to find the correct groupId, assetId, and version.
            """)
    public String getAssetSpec(String groupId, String assetId, String version) {
        return exchangeClient.getAssetSpec(groupId, assetId, version);
    }
}
