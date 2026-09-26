#!/bin/sh
set -eu
TASK_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
TASK_DATA="$HOME/Library/Application Support/thereabout-finances"
mkdir -p "$TASK_DATA"
if [ ! -f "$TASK_DATA/mcp-key" ]; then
  (umask 077; python3 -c 'import secrets; print(secrets.token_urlsafe(48))' > "$TASK_DATA/mcp-key")
fi
FINANCE_MCP_KEY=$(cat "$TASK_DATA/mcp-key")
export FINANCE_MCP_KEY
cd "$TASK_ROOT/backend"
exec mvn spring-boot:run -Dspring-boot.run.profiles=development,finance-local -Dspring-boot.run.arguments=--thereabout.launcher.fetch-icons=false
