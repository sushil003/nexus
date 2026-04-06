# Deployment

## Docker Compose (Recommended)

### 1. Create `.env` file

```bash
NEXUS_KEK=generate-a-strong-random-key-here
NEXUS_ADMIN_PASSWORD=your-secure-password
# Optional
NEXUS_GITHUB_API_KEY=ghp_xxx
NEXUS_SLACK_BOT_TOKEN=xoxb-xxx
EXCALIDRAW_DIR=./excalidraw-files
```

### 2. Start services

```bash
docker compose up -d
```

This starts:
- **PostgreSQL 16** (Alpine, 128MB limit) with health check
- **Nexus app** (256MB limit) with health check, depends on DB

### 3. Verify

```bash
# Check health
curl http://localhost:8080/actuator/health

# Check plugin status via MCP or REST
curl -u admin:YOUR_PASSWORD http://localhost:8080/actuator/health
```

## Docker Compose Configuration

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: nexus
      POSTGRES_USER: nexus
      POSTGRES_PASSWORD: nexus
    ports: ["5432:5432"]
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U nexus"]
      interval: 5s
      retries: 5
    deploy:
      resources:
        limits:
          memory: 128M

  app:
    build: .
    ports: ["127.0.0.1:8080:8080"]    # localhost only
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/nexus
      SPRING_DATASOURCE_USERNAME: nexus
      SPRING_DATASOURCE_PASSWORD: nexus
      NEXUS_KEK: ${NEXUS_KEK}
      NEXUS_ADMIN_PASSWORD: ${NEXUS_ADMIN_PASSWORD}
      NEXUS_PLUGINS_EXCALIDRAW_DIRECTORY: /data/excalidraw
      SPRING_PROFILES_ACTIVE: prod
    volumes:
      - ${EXCALIDRAW_DIR:-./excalidraw-files}:/data/excalidraw
    depends_on:
      db:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 256M

volumes:
  pgdata:
```

## Local Development

### Without Docker (requires Java 25 + PostgreSQL)

```bash
# Start only PostgreSQL
docker compose -f compose-dev.yml up -d

# Run the app
NEXUS_KEK=dev-key ./gradlew :nexus-app:bootRun
```

### With Docker Compose

```bash
docker compose up --build
```

## Connecting AI Agents

### Claude Desktop / Claude Code

Add to MCP config:

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

Generate the base64 value:
```bash
echo -n "admin:YOUR_PASSWORD" | base64
```

## Resource Usage

Target footprint:
- **JVM:** 180-220 MB (G1 GC, virtual threads)
- **PostgreSQL:** ~50 MB idle
- **Total:** ~300 MB

## Backup

PostgreSQL data is stored in the `pgdata` Docker volume. Backup with:

```bash
docker compose exec db pg_dump -U nexus nexus > backup.sql
```

## Updating

```bash
git pull
docker compose up --build -d
```

Flyway handles database migrations automatically on startup.
