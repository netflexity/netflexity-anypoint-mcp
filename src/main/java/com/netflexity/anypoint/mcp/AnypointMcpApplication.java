package com.netflexity.anypoint.mcp;

import com.netflexity.anypoint.mcp.license.LicenseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties(LicenseProperties.class)
public class AnypointMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnypointMcpApplication.class, args);
    }
}
