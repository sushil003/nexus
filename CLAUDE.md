# Nexus — Integration Layer for AI Agents

## Overview
Java 25 + Spring Boot 3.5 tool that exposes integrations (GitHub, Slack, Miro, Excalidraw) to AI agents via MCP tools. Personal use, deployed locally via Docker.

## Project Structure
- **nexus-core/** — Plugin system, DB entities, auth/encryption, permissions, HTTP client, plugin implementations
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
- 4 plugins: GitHub (3 endpoints), Slack (3), Excalidraw (3, local files), Miro (5, OAuth2)
- Encryption: AES-256-GCM + PBKDF2 (600K iterations), envelope encryption (KEK/DEK)
- Permission system: OPEN/CAUTIOUS/STRICT/READONLY modes with approval queue
- Transport: SSE (primary for Docker), stdio (if running JAR directly)
- Execution: EndpointExecutor with @Retryable, hooks, permission checks, event logging

## Key Packages
- `dev.nexus.core.plugin` — NexusPlugin interface, PluginRegistry, AuthType, RiskLevel
- `dev.nexus.core.plugin.impl.*` — GitHub, Slack, Excalidraw, Miro plugin implementations
- `dev.nexus.core.endpoint` — EndpointNode (sealed), EndpointHandler, EndpointExecutor
- `dev.nexus.core.auth` — EncryptionService, KeyManager, KeyManagerFactory, DefaultKeyManager, TokenRefreshService
- `dev.nexus.core.permission` — PermissionEvaluator, PermissionEnforcer, PermissionMode
- `dev.nexus.core.inspect` — InspectService, JsonSchemaToTypeString
- `dev.nexus.core.event` — EventService
- `dev.nexus.core.webhook` — WebhookRouter, WebhookDefinition, WebhookHandler
- `dev.nexus.core.http` — NexusHttpClient (WebClient wrapper with retry + Bearer token)
- `dev.nexus.core.db.entity` — 5 JPA entities (integrations, accounts, entities, events, permissions)
- `dev.nexus.core.db.repository` — Spring Data JPA repositories
- `dev.nexus.core.config` — NexusProperties (@ConfigurationProperties)
- `dev.nexus.core.context` — PluginContext record
- `dev.nexus.core.hook` — BeforeHook, AfterHook, EndpointHooks, BeforeHookResult
- `dev.nexus.core.error` — NexusException, ErrorHandler
- `dev.nexus.app.mcp` — NexusMcpTools (4 @Tool methods)
- `dev.nexus.app.web` — CredentialController, PermissionController, OAuthController, WebhookController
- `dev.nexus.app.security` — SecurityConfig (HTTP Basic, endpoint rules)
- `dev.nexus.app.setup` — NexusInitializer (bootstrap plugins, validate KEK)
- `dev.nexus.app.schedule` — CleanupScheduler (expire permissions, purge events)
- `dev.nexus.app.health` — PluginHealthIndicator

## Environment Variables
- `NEXUS_KEK` — master encryption key (required)
- `NEXUS_ADMIN_PASSWORD` — admin password for REST API + MCP auth (required)
- `NEXUS_GITHUB_API_KEY` — optional, auto-configures GitHub plugin
- `NEXUS_SLACK_BOT_TOKEN` — optional, auto-configures Slack plugin
- `NEXUS_PLUGINS_EXCALIDRAW_DIRECTORY` — optional, directory for .excalidraw files

## REST Endpoints
- `POST /api/credentials/{pluginId}` — set plugin credentials (authenticated)
- `POST /api/permissions/{id}/approve|deny` — approve/deny pending permissions (authenticated)
- `GET /oauth/{pluginId}/start` — start OAuth flow (authenticated)
- `GET /oauth/{pluginId}/callback` — OAuth callback (public)
- `POST /webhooks/{pluginId}` — webhook delivery (public)
- `GET /actuator/health` — health check (public)

## Testing
- Unit tests use Mockito (PermissionEvaluator, EndpointExecutor, InspectService, JsonSchemaToTypeString)
- Integration tests use Testcontainers PostgreSQL (not H2) due to JSONB columns
- `./gradlew test` runs all tests
