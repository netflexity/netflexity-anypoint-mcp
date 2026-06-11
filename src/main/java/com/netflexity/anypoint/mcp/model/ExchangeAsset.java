package com.netflexity.anypoint.mcp.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExchangeAsset {
    private String groupId;
    private String assetId;
    private String version;
    private String name;
    private String type;
    private String description;
    private String createdDate;
}
