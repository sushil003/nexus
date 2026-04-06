# Architecture

## Module Structure

Nexus is a 2-module Gradle project:

- **nexus-core** — Plugin system, database entities, auth/encryption, permissions, HTTP client, plugin implementations. Produces a plain JAR.
- **nexus-app** — Spring Boot application, MCP tools, REST controllers, security config. Produces a bootJar.

## Request Flow

```
AI Agent → SSE/MCP → NexusMcpTools → EndpointExecutor → Plugin Handler → External API
                                          ↓
                                   PermissionEnforcer
                                          ↓
                                   KeyManager (decrypt creds)
                                          ↓
                                   EventService (log)
```

### EndpointExecutor Flow

1. Parse dot-path → plugin ID + endpoint segments (e.g., `github.issues.create`)
2. Look up plugin in `PluginRegistry`, walk `EndpointNode` tree to `Leaf`
3. **Permission check** via `PermissionEnforcer` (once, before retry)
4. **Before hook** (optional) — can abort, modify args, set `passToAfter`
5. **Execute handler** with `@Retryable` (Spring Retry via AOP):
   - Backoff: 1s initial, 2x multiplier, 10s max
   - Max attempts: 3
6. **After hook** (only if before ran AND handler succeeded)
7. **Log event** to `nexus_events` via `EventService`
8. Mark single-use permission as `COMPLETED`

## Plugin System

Plugins implement `NexusPlugin` and are registered as Spring `@Component` beans. `PluginRegistry` collects all via `List<NexusPlugin>` constructor injection.

### EndpointNode (Sealed Interface)

```
EndpointNode
├── Group(Map<String, EndpointNode> children)  — intermediate node
└── Leaf(EndpointHandler, inputType, outputType, EndpointHooks)  — executable endpoint
```

Endpoints form a tree navigated by dot-separated paths. Example: `github.issues.create` walks `Group("issues") → Group("create") → Leaf`.

## Permission System

Static permission matrix:

| Risk \ Mode | OPEN | CAUTIOUS | STRICT | READONLY |
|-------------|------|----------|--------|----------|
| READ        | ALLOW | ALLOW | ALLOW | ALLOW |
| WRITE       | ALLOW | ALLOW | REQUIRE_APPROVAL | DENY |
| DESTRUCTIVE | ALLOW | REQUIRE_APPROVAL | DENY | DENY |

Approval workflow: `PENDING → APPROVED → EXECUTING → COMPLETED | FAILED | DENIED | EXPIRED`

Approvals via `POST /api/permissions/{id}/approve` (authenticated).

## Encryption

- **Cipher:** AES-256-GCM (12-byte IV, 128-bit auth tag)
- **Key derivation:** PBKDF2WithHmacSHA256, 600,000 iterations (2026 OWASP)
- **Envelope encryption:** KEK (env var) encrypts DEKs, DEKs encrypt credential values
- **Formats:**
  - DEK: `salt:iv:authTag:ciphertext`
  - Value: `iv:authTag:ciphertext`

## Database

5 PostgreSQL tables managed by Flyway:

| Table | Purpose |
|-------|---------|
| `nexus_integrations` | Plugin registration + encrypted DEK |
| `nexus_accounts` | Tenant-specific credentials (encrypted config) |
| `nexus_entities` | Plugin-managed data entities |
| `nexus_events` | Execution event log |
| `nexus_permissions` | Approval queue |

## Webhook Processing

Two-level matching via `WebhookRouter`:

1. **Plugin-level** — `PluginWebhookMatcher` quick-filters by header presence (e.g., `x-github-event`)
2. **Webhook-level** — Individual `WebhookDefinition.matcher()` matches specific events

## Security

- MCP endpoints (`/sse`, `/mcp/message`) and `/api/*` require HTTP Basic auth
- Webhooks (`/webhooks/**`) are public (external services must reach them)
- OAuth callbacks (`/oauth/*/callback`) are public
- CSRF disabled (non-browser clients), stateless sessions
