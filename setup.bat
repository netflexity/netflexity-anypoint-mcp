@echo off
setlocal enabledelayedexpansion

echo.
echo Anypoint MCP Setup
echo ==================
echo This will configure Claude Code to connect to the hosted Anypoint MCP server.
echo.

set /p CLIENT_ID="Anypoint Client ID:       "
set /p CLIENT_SECRET="Anypoint Client Secret:   "
set /p ORG_ID="Anypoint Org ID:          "
set /p ENV="Default Environment [Production]: "
if "!ENV!"=="" set ENV=Production

set /p API_KEY="License Key (leave blank for Free tier): "
set /p MCP_URL="MCP Server URL [https://mcp.qflex.io/mcp/sse]: "
if "!MCP_URL!"=="" set MCP_URL=https://mcp.qflex.io/mcp/sse

if "!CLIENT_ID!"=="" goto :missing
if "!CLIENT_SECRET!"=="" goto :missing
if "!ORG_ID!"=="" goto :missing

echo.
echo Registering MCP server with Claude Code...

set CLAUDE_CMD=claude mcp add anypoint "!MCP_URL!" --transport sse ^
  --header "X-Anypoint-Client-Id: !CLIENT_ID!" ^
  --header "X-Anypoint-Client-Secret: !CLIENT_SECRET!" ^
  --header "X-Anypoint-Org-Id: !ORG_ID!" ^
  --header "X-Anypoint-Env: !ENV!"

if not "!API_KEY!"=="" (
  set CLAUDE_CMD=!CLAUDE_CMD! --header "X-API-Key: !API_KEY!"
)

!CLAUDE_CMD!

echo.
echo Done! Verify with: claude mcp list
goto :eof

:missing
echo.
echo Error: Client ID, Client Secret, and Org ID are required.
exit /b 1
