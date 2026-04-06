# Nexus

Integration layer for AI agents. Exposes services (GitHub, Slack, Miro, Excalidraw) as typed, permission-controlled plugins that AI agents discover and invoke via MCP tools.

Built with **Java 25 + Spring Boot 3.5 + Gradle**, deployable locally via Docker.

## Features

- **4 MCP tools** — `nexus_setup`, `list_operations`, `get_schema`, `nexus_run`
- **4 plugins** — GitHub, Slack, Excalidraw (local files), Miro (OAuth2)
- **Permission system** — OPEN / CAUTIOUS / STRICT / READONLY modes with approval queue
- **Encryption** — AES-256-GCM + PBKDF2 (600K iterations), envelope encryption (KEK/DEK)
- **SSE transport** — AI agents connect via `http://localhost:8080/sse`

## Quick Start

### Prerequisites

- Docker and Docker Compose
- A `.env` file with required secrets

### 1. Create `.env`

```bash
NEXUS_KEK=your-master-encryption-key-here
NEXUS_ADMIN_PASSWORD=your-admin-password
# Optional: auto-configure plugins on startup
NEXUS_GITHUB_API_KEY=ghp_xxx
NEXUS_SLACK_BOT_TOKEN=xoxb-xxx
```

### 2. Start

```bash
docker compose up
```

This starts PostgreSQL 16 + Nexus. Wait for the health check to pass.

### 3. Verify

```bash
curl http://localhost:8080/actuator/health
```

### 4. Configure Plugins

```bash
# Set GitHub API key
curl -u admin:YOUR_PASSWORD -X POST http://localhost:8080/api/credentials/github \
  -H "Content-Type: application/json" \
  -d '{"api_key": "ghp_xxx"}'

# Set Slack bot token
curl -u admin:YOUR_PASSWORD -X POST http://localhost:8080/api/credentials/slack \
  -H "Content-Type: application/json" \
  -d '{"bot_token": "xoxb-xxx"}'
```

### 5. Connect AI Agent

Add to your Claude Code MCP config (`~/.claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "nexus": {
      "url": "http://localhost:8080/sse",
      "headers": {
        "Authorization": "Basic <base64(admin:YOUR_PASSWORD)>"
      }
    }
  }
}
```

## Available Plugins

| Plugin | Endpoints | Auth | Transport |
|--------|-----------|------|-----------|
| GitHub | `repos.list`, `issues.list`, `issues.create` | API Key (PAT) | REST API |
| Slack | `channels.list`, `chat.postMessage`, `chat.history` | Bot Token | REST API |
| Excalidraw | `scenes.list`, `scenes.get`, `scenes.create` | None | Local filesystem |
| Miro | `boards.list`, `boards.get`, `items.list`, `stickyNotes.create`, `shapes.create` | OAuth2 | REST API |

## MCP Tools

| Tool | Description |
|------|-------------|
| `nexus_setup` | Check plugin auth status and setup instructions |
| `list_operations` | List all endpoints with risk levels |
| `get_schema` | Get input/output JSON schema for an endpoint |
| `nexus_run` | Execute an endpoint with JSON arguments |

## Project Structure

```
nexus/
├── nexus-core/     # Plugin system, DB, auth, permissions, HTTP client, plugins
├── nexus-app/      # Spring Boot app, MCP tools, REST controllers, security
├── docker-compose.yml
├── Dockerfile
└── docs/           # Documentation
```

## Development

```bash
# Build (requires Java 25)
./gradlew build

# Run locally (starts PostgreSQL via Docker Compose)
./gradlew :nexus-app:bootRun

# Run everything in Docker
docker compose up
```

## Documentation

- [Architecture](docs/architecture.md)
- [Plugin Guide](docs/plugin-guide.md)
- [MCP Tools](docs/mcp-tools.md)
- [Configuration](docs/configuration.md)
- [Deployment](docs/deployment.md)

## License

Private project — personal use only.
