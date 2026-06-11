# Anypoint MCP Server

A [Model Context Protocol (MCP)](https://modelcontextprotocol.io) server for MuleSoft Anypoint Platform. Talk to Anypoint from Claude Code — list apps, fetch logs, inspect queues, search Exchange, and more.

## Tools

| Tool | API | Tier |
|------|-----|------|
| `listEnvironments` / `resolveEnvironment` | Platform | Free |
| `listApplications` / `getApplication` | Runtime Manager (CH1) | Free |
| `getApplicationLogs` | Runtime Manager (CH1) | Free |
| `restartApplication` | Runtime Manager (CH1) | **Pro** |
| `deployApplication` / `scaleWorkers` | Runtime Manager (CH1) | **Pro** |
| `setEnvironmentVariables` | Runtime Manager (CH1) | **Pro** |
| `listCh2Applications` / `getCh2Application` | Runtime Manager (CH2/RTF) | Free |
| `scaleCh2Replicas` / `restartCh2Application` | Runtime Manager (CH2/RTF) | **Pro** |
| `listApis` | API Manager | Free |
| `applyPolicy` / `removePolicy` | API Manager | **Pro** |
| `searchAssets` / `getAssetSpec` | Exchange | Free |
| `listDestinations` / `listRegions` | Anypoint MQ | Free |
| `sendMessage` | Anypoint MQ | **Pro** |
| `purgeQueue` / `createQueue` / `deleteQueue` | Anypoint MQ | **Pro** |
| `queryMetric` / `listAlerts` | Anypoint Monitoring | Free |

## Quick Start (stdio — Claude Code local)

### 1. Prerequisites

- Java 17+
- Connected App with these scopes: `Runtime Manager`, `API Manager`, `Exchange Viewer`, `Anypoint MQ`

### 2. Download the JAR

```bash
curl -L https://github.com/netflexity/netflexity-anypoint-mcp/releases/latest/download/netflexity-anypoint-mcp.jar \
  -o anypoint-mcp.jar
```

### 3. Add to Claude Code

```bash
claude mcp add anypoint -- java \
  -DANYPOINT_CLIENT_ID=your_client_id \
  -DANYPOINT_CLIENT_SECRET=your_client_secret \
  -DANYPOINT_ORG_ID=your_org_id \
  -DANYPOINT_DEFAULT_ENV=Production \
  -jar /path/to/anypoint-mcp.jar
```

Or edit `.claude/mcp.json`:

```json
{
  "mcpServers": {
    "anypoint": {
      "command": "java",
      "args": [
        "-DANYPOINT_CLIENT_ID=your_client_id",
        "-DANYPOINT_CLIENT_SECRET=your_client_secret",
        "-DANYPOINT_ORG_ID=your_org_id",
        "-jar", "/path/to/anypoint-mcp.jar"
      ]
    }
  }
}
```

## Hosted Mode (SSE — Railway)

Deploy on Railway with these env vars:

| Variable | Description |
|----------|-------------|
| `ANYPOINT_CLIENT_ID` | Connected App client ID |
| `ANYPOINT_CLIENT_SECRET` | Connected App client secret |
| `ANYPOINT_ORG_ID` | Anypoint organization ID |
| `ANYPOINT_DEFAULT_ENV` | Default environment name (e.g. `Production`) |
| `ADMIN_KEY` | Secret key for `/admin/audit` endpoint |
| `SPRING_PROFILES_ACTIVE` | Set to `sse` for hosted mode |

Add to Claude Code:

```bash
claude mcp add anypoint-hosted \
  --transport sse \
  --url https://your-railway-app.railway.app/mcp/sse \
  --header "X-API-Key: your_license_key"
```

## Connected App Setup

In Anypoint Platform → Access Management → Connected Apps → Create app:

- **Type**: App acts on its own behalf (client credentials)
- **Scopes**: Design Center, Exchange Viewer, Runtime Manager, API Manager, Anypoint MQ

## Rate Limits

| Tier | Requests/min | Write Tools |
|------|-------------|-------------|
| Free | 60 | Read-only |
| Pro ($49/mo) | 600 | All tools |
| Enterprise ($199/mo) | Unlimited | All tools + audit log |

## Build from Source

```bash
git clone https://github.com/netflexity/netflexity-anypoint-common.git
cd netflexity-anypoint-common && mvn clean install -DskipTests

git clone https://github.com/netflexity/netflexity-anypoint-mcp.git
cd netflexity-anypoint-mcp && mvn clean package -DskipTests
```
