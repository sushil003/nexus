# MCP Tools

Nexus exposes 4 MCP tools via SSE transport. AI agents discover and invoke these tools automatically.

## Transport

- **SSE endpoint:** `http://localhost:8080/sse`
- **Authentication:** HTTP Basic (`admin:YOUR_PASSWORD`)

## Tools

### `nexus_setup`

Check auth status for all plugins and show setup instructions.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `plugin` | string | No | Plugin ID to check specific plugin |

**Example response:**
```
# Nexus Plugin Status

## github
- Auth type: API_KEY
- Required fields: [api_key]
- Status: ✓ Configured

## slack
- Auth type: BOT_TOKEN
- Required fields: [bot_token]
- Status: ✗ Not configured
- Setup: POST /api/credentials/slack with bot_token
```

### `list_operations`

List available endpoint paths with descriptions and risk levels.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `plugin` | string | No | Plugin ID filter |

**Example response:**
```
# Available Operations

| Path | Description | Risk | Irreversible |
|------|-------------|------|--------------|
| github.repos.list | List authenticated user's repositories | READ | No |
| github.issues.create | Create a new issue | WRITE | No |
```

### `get_schema`

Get input/output schema for a specific endpoint.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `path` | string | Yes | Dot-path (e.g., `github.issues.create`) |

**Example response:**
```
# Schema: github.issues.create

**Description:** Create a new issue

## Input
{ owner: string, repo: string, title: string, body?: string }

## Output
object
```

### `nexus_run`

Execute an endpoint by path with JSON arguments.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| `path` | string | Yes | Endpoint path (e.g., `github.repos.list`) |
| `args` | string | No | JSON arguments |

**Examples:**
```
# List repos (no args needed)
path: github.repos.list

# Create an issue
path: github.issues.create
args: {"owner": "myorg", "repo": "myrepo", "title": "Bug report", "body": "Details here"}

# Send Slack message
path: slack.chat.postMessage
args: {"channel": "C01234567", "text": "Hello from Nexus!"}

# Create Excalidraw scene
path: excalidraw.scenes.create
args: {"name": "diagram", "elements": [{"type": "rectangle", "x": 0, "y": 0, "width": 200, "height": 100}]}
```

## Permission Handling

When an operation requires approval (based on risk level and permission mode), `nexus_run` returns a message like:

```
Approval required for github.issues.create (permission ID: abc-123).
Approve via POST /api/permissions/abc-123/approve
```

After approval, re-run the same `nexus_run` call to execute.
