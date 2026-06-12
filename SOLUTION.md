# Anypoint MCP Server — Solution Notes

## What It Is

A stateless MCP (Model Context Protocol) server deployed on Railway at `mcp.qflex.io`.
Lets Claude Code talk directly to MuleSoft Anypoint Platform — list queues, check depth,
peek messages, purge, audit log, usage/billing stats, and more.

## Architecture

- **Runtime:** Spring Boot 3.4.1 + Spring AI 1.0.0 + MCP SDK 0.10.0
- **Transport:** SSE (Server-Sent Events) via Spring WebFlux / Netty
- **Multi-tenant:** credentials passed per-request via HTTP headers (no server-side secrets)
- **Deploy:** Railway auto-deploys from `main` branch on GitHub push (~4 min build)
- **URL:** `https://mcp.qflex.io/mcp/sse`

## Client Setup (Claude Code CLI)

Run `setup.sh` (Unix) or `setup.bat` (Windows) in the repo root — it prompts for credentials
and calls `claude mcp add` with the right headers.

Required headers per request:
- `X-Anypoint-Client-Id` — Connected App client ID
- `X-Anypoint-Client-Secret` — Connected App client secret
- `X-Anypoint-Org-Id` — Anypoint org ID
- `X-Anypoint-Env` — environment name (optional, defaults to "Production")
- `X-Anypoint-Base-Url` — Anypoint base URL (optional, defaults to `https://anypoint.mulesoft.com`)
- `X-API-Key` — license key (optional, used for rate limiting / Pro features)

## Available Tools

| Tool | Description |
|------|-------------|
| `listDestinations` | List queues and exchanges in an environment + region |
| `listRegions` | Discover valid MQ regions for an environment |
| `getQueueDepth` | Real-time message count + in-flight count per queue |
| `getQueueStats` | Throughput stats (received/sent/visible) over a time window |
| `getMqUsage` | 30-day billing/usage summary (billable units, API requests, bytes) |
| `getMqAuditLog` | Config audit log — who created/deleted/modified queues |
| `peekMessages` | Non-destructive browse of up to 10 messages (locks released immediately) |
| `sendMessage` | Send a message to a queue or exchange |
| `purgeQueue` | Drain all messages from a queue (consume-and-ACK loop via Broker API) |
| `createQueue` | Create a new queue (FIFO, encrypted options) |
| `deleteQueue` | Delete a queue permanently |

## Key Implementation Notes

### Tomcat vs Netty
`anypoint-common` (shared library) pulls in `spring-boot-starter-web` → Tomcat.
SSE routes are WebFlux and don't register under Tomcat.
**Fix:** exclude `spring-boot-starter-tomcat` from the `anypoint-common` dependency in `pom.xml`.
Do NOT modify `anypoint-common` itself — it's used by other projects.

### Purge Implementation
Anypoint MQ Admin API has no bulk-delete endpoint. The purge loops via Broker API:
1. `GET .../destinations/{queue}/messages?batchSize=10&lockTtl=60000`
2. `DELETE .../messages/{messageId}?lockId={encodedLockId}` per message
3. Repeats until queue empty (max 10k messages safety cap)

The lockId is base64 with `+`, `/`, `=` — must be URL-encoded with `URLEncoder.encode()` before
putting in a query param. Unencoded `+` → space on server → 500 error.

### Request Timeout
Spring AI MCP server default request timeout is **20 seconds**. Set to `300s` in `application.yml`
to allow long-running operations (purge of large queues).

### Auth Context Propagation
Per-request Anypoint credentials are extracted from headers in `ApiKeyFilter` and stored in:
- `AnypointContextHolder` (ThreadLocal) for sync access
- Reactor context via `.contextWrite()` for async propagation
- `Hooks.enableAutomaticContextPropagation()` + `ThreadLocalAccessor` bridges the two

### API Tracing
All outbound Anypoint API calls are logged via `ExchangeFilterFunction` on the `WebClient` bean:
```
INFO anypoint.api --> GET https://anypoint.mulesoft.com/mq/admin/api/v1/...
INFO anypoint.api <-- 200 https://anypoint.mulesoft.com/mq/admin/api/v1/...
```

## SSE Transport — Known Limitation

**Symptom:** After a Railway redeploy, Claude Code's MCP session holds a stale SSE stream.
`claude mcp list` shows ✓ Connected (it re-probes fine) but tool calls time out because
responses can't come back on the dead stream.

**Workaround:** After every Railway redeploy, before firing tool calls:
- `/mcp` → select `anypoint` → **Reconnect** in Claude Code
- OR `/exit` → reopen Claude Code

This is inherent to the SSE (HTTP+SSE) transport, not a bug in this server.

## TODO: Upgrade to Streamable HTTP Transport

The newer MCP "Streamable HTTP" transport (`type: http` in Claude Code config) is stateless —
no persistent SSE stream, no stale-session problem after server restarts.

**Blockers as of June 2026:**
- Spring AI 1.0.x hardwires `WebFluxSseServerTransportProvider` in its autoconfiguration
- MCP SDK 0.11.0+ has `WebFluxStreamableServerTransportProvider` but Spring AI doesn't wire it
- Spring AI 2.0.0 is in RC phase (2.0.0-RC2 as of this writing) — too risky for prod

**When Spring AI 2.0.0 GA ships:**
1. Upgrade `spring-ai.version` in `pom.xml` to 2.0.0
2. Check if `McpWebFluxServerAutoConfiguration` now supports `WEBFLUX_STREAMABLE` transport type
3. Update `application.yml`: `transport: WEBFLUX_STREAMABLE` (or whatever the new enum value is)
4. Update Claude Code MCP config: `type: http`, `url: https://mcp.qflex.io/mcp`
5. Remove `sse-endpoint` and `sse-message-endpoint` from `application.yml`

**Alternative (manual wiring, no Spring AI upgrade needed):**
- Override `mcp-spring-webflux` version to `0.18.x` in `pom.xml` dependencyManagement
- Exclude `McpWebFluxServerAutoConfiguration` 
- Register `WebFluxStreamableServerTransportProvider` as a `@Bean` manually
- Wire it into the MCP server as the transport provider

This is more involved and risks API incompatibilities between Spring AI 1.0.x and SDK 0.18.x.
Wait for 2.0.0 GA unless the SSE reconnect friction becomes a real operational problem.

## Repo

`netflexity/netflexity-anypoint-mcp` on GitHub (private) — Railway deploys from `main`.
