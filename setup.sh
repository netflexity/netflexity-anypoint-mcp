#!/usr/bin/env bash
set -e

echo ""
echo "Anypoint MCP Setup"
echo "=================="
echo "This will configure Claude Code to connect to the hosted Anypoint MCP server."
echo ""

read -p "Anypoint Client ID:       " CLIENT_ID
read -sp "Anypoint Client Secret:   " CLIENT_SECRET
echo ""
read -p "Anypoint Org ID:          " ORG_ID
read -p "Default Environment [Production]: " ENV
ENV=${ENV:-Production}
read -p "License Key (leave blank for Free tier): " API_KEY
read -p "MCP Server URL [https://mcp.qflex.io/mcp/sse]: " MCP_URL
MCP_URL=${MCP_URL:-https://mcp.qflex.io/mcp/sse}

if [ -z "$CLIENT_ID" ] || [ -z "$CLIENT_SECRET" ] || [ -z "$ORG_ID" ]; then
  echo ""
  echo "Error: Client ID, Client Secret, and Org ID are required."
  exit 1
fi

HEADERS=(
  "--header" "X-Anypoint-Client-Id: $CLIENT_ID"
  "--header" "X-Anypoint-Client-Secret: $CLIENT_SECRET"
  "--header" "X-Anypoint-Org-Id: $ORG_ID"
  "--header" "X-Anypoint-Env: $ENV"
)
[ -n "$API_KEY" ] && HEADERS+=("--header" "X-API-Key: $API_KEY")

echo ""
echo "Registering MCP server with Claude Code..."

claude mcp add anypoint "$MCP_URL" \
  --transport sse \
  "${HEADERS[@]}"

echo ""
echo "Done! Verify with: claude mcp list"
