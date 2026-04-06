# Nexus — Integration Layer for AI Agents

## Overview
Java 25 + Spring Boot 4.0.5 tool that exposes integrations (GitHub, Slack, Miro, Excalidraw) to AI agents via MCP tools. Personal use, deployed locally via Docker.

## Project Structure
- **nexus-core/** — Plugin system, DB entities, auth/encryption, permissions, HTTP client, plugins
- **nexus-app/** — Spring Boot app, MCP tools, REST controllers, security config

## Key Commands
```bash
./gradlew build              # compile + test
./gradlew :nexus-app:bootRun # run locally (needs PostgreSQL via compose-dev.yml)
docker compose up             # run everything in Docker (production mode)
```

## Architecture
- 2 Gradle modules: `nexus-core` (library JAR), `nexus-app` (Spring Boot JAR)
- 4 MCP tools: `nexus_setup`, `list_operations`, `get_schema`, `nexus_run`
- 4 plugins: GitHub, Slack, Excalidraw (local files), Miro
- Encryption: AES-256-GCM + PBKDF2 (600K iterations), envelope encryption (KEK/DEK)
- Permission system: OPEN/CAUTIOUS/STRICT/READONLY modes with approval queue
- Transport: SSE (primary for Docker), stdio (if running JAR directly)

## Key Packages
- `dev.nexus.core.plugin` — NexusPlugin interface, PluginRegistry
- `dev.nexus.core.endpoint` — EndpointNode (sealed), EndpointExecutor
- `dev.nexus.core.auth` — EncryptionService, KeyManager
- `dev.nexus.core.permission` — PermissionEvaluator, PermissionEnforcer
- `dev.nexus.core.db.entity` — JPA entities (5 tables)
- `dev.nexus.core.plugin.impl.*` — Plugin implementations
- `dev.nexus.app.mcp` — NexusMcpTools (@McpTool)
- `dev.nexus.app.web` — REST controllers
- `dev.nexus.app.security` — SecurityConfig

## Environment Variables
- `NEXUS_KEK` — master encryption key (required)
- `NEXUS_ADMIN_PASSWORD` — admin password for REST API + MCP auth (required)
- `NEXUS_GITHUB_API_KEY` — optional, auto-configures GitHub plugin
- `NEXUS_SLACK_BOT_TOKEN` — optional, auto-configures Slack plugin

## Testing
- All tests use Testcontainers PostgreSQL (not H2) due to JSONB columns
- `./gradlew test` runs all tests
