package com.netflexity.anypoint.mcp.license;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "license")
public class LicenseProperties {

    private Map<String, String> keys = new HashMap<>();

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys;
    }
}
