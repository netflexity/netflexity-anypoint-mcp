package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ManagedApi {
    private String id;
    private String name;
    private String version;
    private String endpoint;
    private String status;
    private List<String> policies;
}
