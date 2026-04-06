# Configuration

## Environment Variables

### Required

| Variable | Description |
|----------|-------------|
| `NEXUS_KEK` | Master encryption key (Key Encryption Key). Must be set — no default. |
| `NEXUS_ADMIN_PASSWORD` | Admin password for REST API and MCP auth. Default: `changeme` (change in production). |

### Optional — Plugin Auto-Configuration

| Variable | Description |
|----------|-------------|
| `NEXUS_GITHUB_API_KEY` | GitHub Personal Access Token. Auto-configures GitHub plugin on startup. |
| `NEXUS_SLACK_BOT_TOKEN` | Slack Bot Token. Auto-configures Slack plugin on startup. |

### Optional — Plugin Settings

| Variable | Default | Description |
|----------|---------|-------------|
| `NEXUS_PLUGINS_EXCALIDRAW_DIRECTORY` | `./excalidraw-files` | Directory for local .excalidraw files |

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/nexus` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `nexus` | DB username |
| `SPRING_DATASOURCE_PASSWORD` | `nexus` | DB password |

## Application Properties

### `application.yml` (base)

```yaml
spring:
  threads:
    virtual:
      enabled: true          # Virtual threads for all request handling
  datasource:
    hikari:
      maximum-pool-size: 5   # Keep small for local deployment
      minimum-idle: 2
  ai:
    mcp:
      server:
        name: nexus
        version: 1.0.0

nexus:
  kek: ${NEXUS_KEK:}
  admin-password: ${NEXUS_ADMIN_PASSWORD:changeme}
  permission:
    mode: cautious           # OPEN | CAUTIOUS | STRICT | READONLY
  plugins:
    excalidraw:
      directory: ${NEXUS_PLUGINS_EXCALIDRAW_DIRECTORY:./excalidraw-files}
```

### Permission Modes

| Mode | READ | WRITE | DESTRUCTIVE |
|------|------|-------|-------------|
| `OPEN` | Allow | Allow | Allow |
| `CAUTIOUS` | Allow | Allow | Require approval |
| `STRICT` | Allow | Require approval | Deny |
| `READONLY` | Allow | Deny | Deny |

### Spring Profiles

- **`dev`** — Lazy initialization, debug logging, Docker Compose auto-start (`compose-dev.yml`)
- **`prod`** — Eager initialization, fail-fast on misconfiguration

## Security Endpoints

| Path | Auth | Description |
|------|------|-------------|
| `POST /api/credentials/{pluginId}` | Basic | Set plugin credentials |
| `POST /api/permissions/{id}/approve` | Basic | Approve pending permission |
| `POST /api/permissions/{id}/deny` | Basic | Deny pending permission |
| `GET /oauth/{pluginId}/start` | Basic | Start OAuth flow |
| `GET /oauth/{pluginId}/callback` | Public | OAuth callback (provider redirects here) |
| `POST /webhooks/{pluginId}` | Public | Webhook delivery endpoint |
| `GET /actuator/health` | Public | Health check |
| `GET /sse` | Basic | MCP SSE transport |
