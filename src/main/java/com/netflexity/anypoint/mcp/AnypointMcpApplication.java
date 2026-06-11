package com.netflexity.anypoint.mcp;

import com.netflexity.anypoint.mcp.context.AnypointContextHolder;
import com.netflexity.anypoint.mcp.context.AnypointRequestContext;
import com.netflexity.anypoint.mcp.license.LicenseProperties;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import reactor.core.publisher.Hooks;

@SpringBootApplication
@EnableCaching
@EnableConfigurationProperties(LicenseProperties.class)
public class AnypointMcpApplication {

    public static void main(String[] args) {
        // Propagate AnypointRequestContext through Reactor thread switches
        Hooks.enableAutomaticContextPropagation();
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                new ThreadLocalAccessor<AnypointRequestContext>() {
                    @Override
                    public Object key() { return AnypointRequestContext.class; }

                    @Override
                    public AnypointRequestContext getValue() { return AnypointContextHolder.get(); }

                    @Override
                    public void setValue(AnypointRequestContext value) { AnypointContextHolder.set(value); }

                    @Override
                    public void reset() { AnypointContextHolder.clear(); }
                });
        SpringApplication.run(AnypointMcpApplication.class, args);
    }
}
