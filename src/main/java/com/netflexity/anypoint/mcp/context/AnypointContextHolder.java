package com.netflexity.anypoint.mcp.context;

public class AnypointContextHolder {

    private static final ThreadLocal<AnypointRequestContext> HOLDER = new InheritableThreadLocal<>();

    public static void set(AnypointRequestContext ctx) {
        HOLDER.set(ctx);
    }

    public static AnypointRequestContext get() {
        return HOLDER.get();
    }

    public static AnypointRequestContext require() {
        AnypointRequestContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new IllegalStateException(
                    "No Anypoint context — missing X-Anypoint-Client-Id / X-Anypoint-Org-Id headers");
        }
        return ctx;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
