# Nexus — Spring Boot Implementation Plan

## Context

Inspired by Corsair (a TypeScript/Node.js integration layer for AI agents), **Nexus** is a Java equivalent that lets developers wire up services (GitHub, Slack, etc.) as typed, permission-controlled plugins that AI agents discover and invoke via MCP tools. Built with **Java 25 (LTS) + Spring Boot 4.0.5 + Gradle (Groovy DSL)**, deployable locally via Docker. Designed to be lightweight and resource-efficient (target: 180-220 MB JVM mode).

All documentation lives inside the repo as markdown.

### GitHub Repo Setup
- Create new **public** GitHub repo: `nexus` (under your account)
- Initialize with `main` branch, `.gitignore` (Gradle), `CLAUDE.md`
- Commit Gradle wrapper (`gradle/wrapper/`, `gradlew`, `gradlew.bat`) — required for Docker builds
- **`CLAUDE.md`** at repo root — essential for Claude Code (mobile/web) sessions:
  - Project overview, module structure, how to build/test
  - Key architecture decisions and where things live
  - Common commands (`./gradlew build`, `docker compose up`, etc.)

### Development Workflow
- **Writing code:** Claude Code app (mobile/web) — no local IDE needed
  - All coding, file creation, and edits done through Claude Code
  - Push commits directly to GitHub from Claude Code
  - GitHub Actions CI auto-builds + tests on every push
  - Check CI status via `gh` CLI or GitHub mobile app
- **Running Nexus locally** (on your laptop, for personal use):
  - `git pull` the latest code
  - `docker compose up` — starts PostgreSQL + Nexus app
  - AI agents (Claude Desktop, etc.) connect to `http://localhost:8080/sse` (SSE transport)
  - No IDE or Java installation needed on laptop — Docker handles everything

---

## Key Design Decisions (from vetting)

| Decision | Choice | Why |
|----------|--------|-----|
| Modules | **2 modules** (`nexus-core`, `nexus-app`) | Core+DB are tightly coupled; MCP is 1 class; plugins are packages not modules |
| Encryption | **javax.crypto + PBKDF2** (600K iterations) | Built-in AES-256-GCM + PBKDF2. 600K iterations per 2026 OWASP guidance |
| Retry | **`@Retryable` + WebClient `retryWhen()`** | Spring Boot 4 has native retry; complementary approaches for method vs HTTP level |
| Permissions | **Simplified** | Keep matrix + DB records; simple REST approve/deny (no token URLs) |
| Multi-tenancy | **Deferred** | Keep `tenant_id` in schema (hardcode `"default"`), build `withTenant()` later |
| Events | **Events table only** | Direct DB inserts; no `ApplicationEventPublisher` for now |
| Lombok | **Dropped** | Java 25 records for DTOs; manual getters for 5 JPA entity classes |
| GC | **G1 (default)** | No special GC flags needed — G1 handles small containers well |
| OpenAPI | **Deferred** | `springdoc-openapi 3.0.2` broken with Jackson 3 in SB4 |
| Java version | **Java 25 (LTS)** | 25 is current LTS; Temurin 26 Alpine not available; LTS = long-term support |
| Excalidraw | **Replaced with local file-based plugin** | No public REST API exists; use `.excalidraw` JSON files instead |
| MCP transport | **SSE primary** (Docker); stdio only if running JAR directly | Can't pipe stdio into Docker container |

---

## 1. Project Structure — 2 Gradle Modules

```
nexus/
├── CLAUDE.md                           # Project context for Claude Code sessions
├── .gitignore                          # build/, .gradle/, *.class, .env, *.jar, .idea/
├── settings.gradle                     # rootProject.name='nexus'; include 'nexus-core','nexus-app'
├── build.gradle                        # root: Java 25 toolchain, Spring Boot 4.0.5 plugin
├── gradle.properties                   # shared version properties (spring-retry, jsonschema-generator)
├── gradlew, gradlew.bat                # Gradle wrapper scripts (must be committed)
├── gradle/wrapper/                     # Gradle wrapper JAR + properties
├── docker-compose.yml                  # PostgreSQL 16 + app
├── Dockerfile                          # multi-stage (JDK build → JRE runtime)
├── .github/
│   └── workflows/
│       └── ci.yml                      # GitHub Actions: build + test on push/PR
├── docs/                               # all documentation (markdown)
│   ├── architecture.md
│   ├── plugin-guide.md
│   ├── mcp-tools.md
│   ├── configuration.md
│   └── deployment.md
│
├── nexus-core/                         # Plugin system + DB + Auth + Permissions + HTTP + Plugins
│   ├── build.gradle
│   └── src/main/java/dev/nexus/core/
│       ├── plugin/                     # NexusPlugin, PluginRegistry, AuthType, PluginAuthConfig
│       ├── endpoint/                   # EndpointNode (sealed), EndpointHandler, EndpointExecutor
│       ├── hook/                       # EndpointHooks, BeforeHookResult
│       ├── webhook/                    # WebhookDefinition, WebhookMatcher, WebhookRouter
│       ├── auth/                       # EncryptionService, KeyManager, KeyManagerFactory
│       ├── permission/                 # PermissionEvaluator, PermissionEnforcer, PermissionMode
│       ├── inspect/                    # InspectService, JsonSchemaToTypeString
│       ├── http/                       # NexusHttpClient (WebClient wrapper)
│       ├── error/                      # NexusException, ErrorHandler
│       ├── event/                      # EventService (direct DB insert)
│       ├── context/                    # PluginContext (resolved key, tenant, HTTP client, DB refs)
│       ├── config/                     # NexusProperties (@ConfigurationProperties)
│       ├── db/
│       │   ├── entity/                 # 5 JPA entities
│       │   └── repository/             # Spring Data JPA repositories
│       └── plugin/impl/               # Plugin implementations
│           ├── github/                 # GitHubPlugin, endpoints, webhooks
│           ├── slack/                  # SlackPlugin, endpoints, webhooks
│           ├── excalidraw/             # ExcalidrawPlugin (local .excalidraw file CRUD)
│           └── miro/                   # MiroPlugin, endpoints, webhooks
│
└── nexus-app/                          # Spring Boot app, MCP tools, controllers, security
    ├── build.gradle
    └── src/
        ├── main/java/dev/nexus/app/
        │   ├── NexusApplication.java      # @ComponentScan, @EntityScan, @EnableJpaRepositories,
        │   │                              # @EnableRetry, @EnableScheduling, @EnableJpaAuditing
        │   ├── mcp/
        │   │   └── NexusMcpTools.java      # 4 @McpTool methods
        │   ├── web/
        │   │   ├── WebhookController.java  # POST /webhooks/{pluginId} (public)
        │   │   ├── PermissionController.java # POST /api/permissions/{id}/approve|deny
        │   │   ├── CredentialController.java # POST /api/credentials/{pluginId} (set API keys)
        │   │   └── OAuthController.java    # GET /oauth/{pluginId}/start + callback
        │   ├── security/
        │   │   └── SecurityConfig.java
        │   ├── schedule/
        │   │   └── CleanupScheduler.java   # @Scheduled: expire permissions, purge events
        │   ├── health/
        │   │   └── PluginHealthIndicator.java
        │   └── setup/
        │       └── NexusInitializer.java   # Bootstrap integrations + DEKs
        └── main/resources/
            ├── application.yml             # base config (virtual threads, HikariCP)
            ├── application-dev.yml         # dev: lazy-init, debug logging
            ├── application-prod.yml        # prod: strict security
            └── db/migration/
                └── V1__init.sql
```

---

## 2. Key Dependencies (Verified for Spring Boot 4.0.5)

| Module | Dependencies |
|--------|-------------|
| `nexus-core` | `spring-boot-starter-web`, `spring-boot-starter-webflux` (for WebClient only — not full reactive stack), `spring-boot-starter-oauth2-client`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `spring-boot-starter-aop` (required for `@Retryable` AOP proxying), `org.flywaydb:flyway-core`, `org.flywaydb:flyway-database-postgresql`, `org.springframework.retry:spring-retry:2.0.11`, `com.github.victools:jsonschema-generator:5.0.0`, `org.postgresql:postgresql` (runtimeOnly) |
| `nexus-app` | `spring-boot-starter-actuator`, `org.springframework.ai:spring-ai-starter-mcp-server-webmvc:1.0.0`, `spring-boot-docker-compose` (developmentOnly), `nexus-core` |
| Dev | `spring-boot-devtools` (developmentOnly), `spring-boot-configuration-processor` (annotationProcessor) |
| Test | `spring-boot-starter-test`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter` |

**All artifact names verified against Maven Central** (curl queries to search.maven.org):
- `spring-boot-starter-web` ✓ (NOT `spring-boot-starter-webmvc` — that doesn't exist)
- `spring-boot-starter-webflux` ✓ (provides WebClient — no separate `webclient` starter exists)
- `spring-boot-starter-oauth2-client` ✓ (NOT `security-oauth2-client`)
- `spring-boot-starter-aop` ✓ (NOT `spring-boot-starter-aspectj` — that doesn't exist)
- `org.flywaydb:flyway-core` + `flyway-database-postgresql` ✓ (no `spring-boot-starter-flyway` exists)
- `spring-boot-starter-test` ✓ (includes MockMvc — no separate `webmvc-test` starter exists)
- `org.springframework.ai:spring-ai-starter-mcp-server-webmvc:1.0.0` ✓ (GA release, SSE+stdio)
- `spring-boot-docker-compose` ✓

> **Spring AI MCP** is at GA 1.0.0 (not milestone). Pin version explicitly since it's not in the Spring Boot BOM. Must also import Spring AI BOM or specify version directly.

### Gradle Build Configuration (Critical)

**Root `build.gradle`:**
```groovy
plugins {
    id 'org.springframework.boot' version '4.0.5' apply false  // only nexus-app uses it
    id 'io.spring.dependency-management' version '1.1.7'
}
subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'
    java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }  // 25 (matches Docker image)
    dependencyManagement {
        imports {
            mavenBom org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES
        }
    }
    tasks.withType(JavaCompile).configureEach {
        options.compilerArgs << '-parameters'  // preserve method param names for @McpToolParam
    }
}
```

**`nexus-core/build.gradle`** (library module — must NOT produce bootJar):
```groovy
// Do NOT apply org.springframework.boot plugin here
jar { enabled = true }       // produce plain JAR for nexus-app to depend on

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-aop'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-database-postgresql'
    implementation 'org.springframework.retry:spring-retry:2.0.11'
    implementation 'com.github.victools:jsonschema-generator:5.0.0'
    runtimeOnly 'org.postgresql:postgresql'
}
```

**`nexus-app/build.gradle`** (application module):
```groovy
plugins { id 'org.springframework.boot' }
dependencies {
    implementation project(':nexus-core')
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.ai:spring-ai-starter-mcp-server-webmvc:1.0.0'
    developmentOnly 'org.springframework.boot:spring-boot-docker-compose'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'org.testcontainers:junit-jupiter'
}
```

**`NexusApplication.java`** (critical annotations for multi-module):
```java
@SpringBootApplication(scanBasePackages = {"dev.nexus.app", "dev.nexus.core"})  // scan both modules
@EntityScan("dev.nexus.core.db.entity")              // find JPA entities in nexus-core
@EnableJpaRepositories("dev.nexus.core.db.repository") // find Spring Data repos in nexus-core
@EnableRetry                   // activate @Retryable AOP proxying (requires starter-aop)
@EnableScheduling              // activate @Scheduled methods in CleanupScheduler
@EnableJpaAuditing             // activate @CreatedDate/@LastModifiedDate on entities
public class NexusApplication { ... }
```

> Without `scanBasePackages`, `@EntityScan`, and `@EnableJpaRepositories`, Spring Boot only scans `dev.nexus.app` — beans, entities, and repositories in `dev.nexus.core` are **never found**. This is a guaranteed runtime failure in multi-module projects.

**JPA JSONB column mapping** — all entities with JSONB fields must use:
```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(columnDefinition = "jsonb")
private Map<String, Object> config;  // or JsonNode
```
Without `@JdbcTypeCode(SqlTypes.JSON)`, Hibernate maps to VARCHAR/TEXT → runtime error with PostgreSQL jsonb columns.

### Docker Compose for Development

`spring-boot-docker-compose` will auto-start `docker-compose.yml` during `bootRun`. Since our `docker-compose.yml` includes the `app` service (which would conflict), create a separate **`compose-dev.yml`** with only the DB:

```yaml
# compose-dev.yml — used by spring-boot-docker-compose during bootRun
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: nexus
      POSTGRES_USER: nexus
      POSTGRES_PASSWORD: nexus
    ports: ["5432:5432"]
```

Configure in `application-dev.yml`:
```yaml
spring:
  docker:
    compose:
      file: compose-dev.yml
```

---

## 3. Core Module Design

### 3.1 Plugin System — `NexusPlugin` interface

```java
public interface NexusPlugin {
    String getId();                                         // "github", "slack"
    AuthType getDefaultAuthType();                          // API_KEY, OAUTH2
    EndpointNode getEndpoints();                            // nested endpoint tree
    Map<String, WebhookDefinition> getWebhooks();
    Optional<PluginWebhookMatcher> getPluginWebhookMatcher();
    Map<String, EndpointMeta> getEndpointMeta();            // risk + description per path
    Map<String, EndpointSchemas> getEndpointSchemas();      // JSON Schema per endpoint
    PluginAuthConfig getAuthConfig();                        // declares credential field names
    Optional<OAuthConfig> getOAuthConfig();
    Optional<ErrorHandler> getErrorHandler();
}

// Enums:
public enum AuthType { API_KEY, OAUTH2, BOT_TOKEN, NONE }
public enum RiskLevel { READ, WRITE, DESTRUCTIVE }
public enum TokenAuthMethod { CLIENT_SECRET_POST, CLIENT_SECRET_BASIC }

// Supporting types (all in plugin/ or endpoint/ packages):
public record PluginAuthConfig(List<String> requiredFields) {}  // e.g., ["api_key"] or ["client_id","client_secret"]
public record EndpointSchemas(JsonNode inputSchema, JsonNode outputSchema) {}
public record EndpointMeta(RiskLevel riskLevel, String description, boolean irreversible) {}
public record OAuthConfig(String providerName, String authUrl, String tokenUrl,
                          List<String> scopes, TokenAuthMethod tokenAuthMethod,
                          boolean requiresRegisteredRedirect, Map<String,String> authParams) {}
public record PluginContext(NexusHttpClient httpClient, KeyManager keyManager,
                            String tenantId, EntityRepository entityRepo) {}

// PluginWebhookMatcher — quick plugin-level filter before individual matchers
@FunctionalInterface
public interface PluginWebhookMatcher {
    boolean matches(Map<String, String> headers);  // e.g., check x-github-event header exists
}

// ErrorHandler — plugin-specific error classification for retry decisions
public interface ErrorHandler {
    boolean isRetryable(Exception ex);
    int getMaxAttempts();
}
```

Plugins live as packages inside `nexus-core/plugin/impl/`. Registered as `@Component` beans. `PluginRegistry` collects all via `List<NexusPlugin>` injection.

> **Extensibility:** Currently plugins are packages within `nexus-core`. To add a plugin, add a new package and `@Component` class. For future external plugins, extract to separate Gradle modules or use `ServiceLoader`.

### 3.2 Endpoint Tree — sealed interface + records

```java
public sealed interface EndpointNode {
    record Group(Map<String, EndpointNode> children) implements EndpointNode {}
    record Leaf(EndpointHandler handler, Class<?> inputType, Class<?> outputType,
                EndpointHooks hooks) implements EndpointNode {}
}

@FunctionalInterface
public interface EndpointHandler {
    Object execute(PluginContext ctx, Object args) throws NexusException;
}
```

### 3.3 EndpointExecutor — Execution Flow

1. Parse dot-path → plugin ID + endpoint segments
2. Lookup plugin in `PluginRegistry`, walk `EndpointNode` tree to `Leaf`
3. **Permission check** via `PermissionEnforcer` (once, before retry)
4. **Before hook** (optional) — can abort, modify args, set `passToAfter`
5. **Execute handler** with `@Retryable` (Spring Retry via AOP):
   - `@Backoff(delay=1000, multiplier=2, maxDelay=10000)`
   - Max attempts: 3 (configurable per plugin via `ErrorHandler`)
   - Complemented by WebClient `retryWhen()` for HTTP-level retry + `Retry-After` header parsing
6. **After hook** (only if before ran AND handler succeeded)
7. **Log event** to `nexus_events` via `EventService`
8. Mark single-use permission as `COMPLETED` if applicable

### 3.4 Permission System (Simplified)

Static permission matrix (fixed):

| Risk \ Mode | OPEN | CAUTIOUS | STRICT | READONLY |
|-------------|------|----------|--------|----------|
| READ | ALLOW | ALLOW | ALLOW | ALLOW |
| WRITE | ALLOW | ALLOW | REQUIRE_APPROVAL | DENY |
| DESTRUCTIVE | ALLOW | REQUIRE_APPROVAL | DENY | DENY |

Simplified approval: `POST /api/permissions/{id}/approve` (authenticated). No token-based review URLs.

Lifecycle: `PENDING → APPROVED → EXECUTING → COMPLETED | FAILED | DENIED | EXPIRED`

### 3.5 Auth & Encryption — javax.crypto

- **Cipher:** AES-256-GCM (12-byte IV, 128-bit auth tag)
- **Key derivation:** PBKDF2WithHmacSHA256 — **600,000 iterations** (per 2026 OWASP guidelines)
- **DEK:** 32 random bytes, base64-encoded
- **Formats:** DEK encryption: `salt:iv:authTag:data` (4 parts); data encryption: `iv:authTag:data` (3 parts)
- Master KEK via env var `NEXUS_KEK` (⚠️ must be set — no default in production)
- **Fail-fast validation** in `NexusInitializer`:
  1. Throw `IllegalStateException("NEXUS_KEK must be set")` if blank/missing
  2. Attempt to decrypt one existing DEK from `nexus_integrations` — if decryption fails, log `"NEXUS_KEK does not match the key used to encrypt existing data"` and shut down
  3. This catches accidental KEK changes (env var typo, container rebuild with different `.env`)

### 3.6 Credential Management UX

**How users add API keys** — `CredentialController`:
- `POST /api/credentials/{pluginId}` with JSON body: `{"api_key": "ghp_xxx"}`
- Validates fields against plugin's `PluginAuthConfig.requiredFields()`
- Encrypts values with DEK, stores in `nexus_accounts.config` JSONB
- Also supported via env vars: `NEXUS_GITHUB_API_KEY`, `NEXUS_SLACK_BOT_TOKEN` (read on startup by `NexusInitializer`)
- **Not** via MCP `nexus_setup` — credential writes require authenticated REST endpoint to prevent unauthenticated injection

### 3.7 Security Config

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    // Public endpoints (no auth):
    //   POST /webhooks/{pluginId}       — external webhook delivery
    //   GET  /actuator/health           — health checks
    //   GET  /oauth/{pluginId}/callback — OAuth provider redirects here
    //
    // Authenticated endpoints (HTTP Basic — nexus.admin.password):
    //   POST /api/credentials/{pluginId}
    //   POST /api/permissions/{id}/approve|deny
    //   GET  /oauth/{pluginId}/start
    //   GET  /sse, POST /mcp/message    — MCP SSE transport + message endpoint
    //
    // CSRF disabled for all endpoints (non-browser clients)
    // Session management: stateless
}
```

**Security details:**
- `/api/*` and MCP endpoints (`/sse`, `/mcp/message`) require HTTP Basic auth with `nexus.admin.password`
- `nexus_setup` MCP tool does **NOT** accept credentials directly — credentials must go through the authenticated `POST /api/credentials/{pluginId}` endpoint. This prevents unauthenticated credential injection via MCP.
- Admin password: `NEXUS_ADMIN_PASSWORD` env var. **Fail-fast on startup** if using the default `changeme` value in `prod` profile (same pattern as KEK validation).
- Webhook endpoints are public (external services must reach them) but verified via per-plugin signature checks (HMAC-SHA256, **constant-time comparison** via `MessageDigest.isEqual()`)
- Webhook signing secrets stored as a field in `PluginAuthConfig.requiredFields` (e.g., `webhook_secret`), encrypted alongside API keys in `nexus_accounts.config`

---

## 4. Database Layer

### Flyway `V1__init.sql` — 5 Tables

| Table | Key Columns | Indexes |
|-------|-------------|---------|
| `nexus_integrations` | id (UUID PK), name (UNIQUE), config (JSONB), dek (TEXT), created_at, updated_at | `name` |
| `nexus_accounts` | id (UUID PK), tenant_id (default `'default'`), integration_id (FK), config (JSONB), dek (TEXT), created_at, updated_at | `(tenant_id, integration_id)` UNIQUE |
| `nexus_entities` | id (UUID PK), account_id (FK), entity_id, entity_type, version, data (JSONB), created_at, updated_at | `(account_id, entity_id, entity_type)` UNIQUE |
| `nexus_events` | id (UUID PK), account_id (FK), event_type, payload (JSONB), status (VARCHAR), created_at, updated_at | `(account_id, status)` |
| `nexus_permissions` | id (UUID PK), plugin, endpoint, args (TEXT), tenant_id, status (VARCHAR), expires_at (TIMESTAMP), error (TEXT), created_at, updated_at | `status`, `expires_at` |

All tests use **Testcontainers PostgreSQL** (not H2) — JSONB is PostgreSQL-specific.

---

## 5. MCP Tool Layer

```java
@Component
public class NexusMcpTools {
    @McpTool(description = "Check auth status for all plugins, show setup instructions")
    public String nexusSetup(@McpToolParam(description = "Optional: plugin ID") String plugin) { ... }

    @McpTool(description = "List available endpoint paths with descriptions and risk levels")
    public String listOperations(@McpToolParam(description = "Optional: plugin ID filter") String plugin) { ... }

    @McpTool(description = "Get input/output schema for a specific endpoint")
    public String getSchema(@McpToolParam(description = "Dot-path, e.g. github.issues.create") String path) { ... }

    @McpTool(description = "Execute an endpoint by path with JSON arguments")
    public String nexusRun(@McpToolParam(description = "Endpoint path") String path,
                           @McpToolParam(description = "JSON args") String args) { ... }
}
```

**Transport: SSE** is primary (works with Docker). Stdio available if running JAR directly.

**Claude Code MCP config** (for your laptop):
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

---

## 6. Plugins (4 Initial)

### GitHub (3 endpoints + 1 webhook)
- `github.repos.list` (READ), `github.issues.list` (READ), `github.issues.create` (WRITE)
- Webhook: `github.push` (HMAC-SHA256 via `x-hub-signature-256`)
- Auth: Personal Access Token (API key)
- API: `https://api.github.com`

### Slack (3 endpoints + 1 webhook)
- `slack.channels.list` (READ), `slack.chat.postMessage` (WRITE), `slack.chat.history` (READ)
- Webhook: `slack.message` (request signing verification)
- Auth: Bot Token (API key) or OAuth2
- API: `https://slack.com/api`

### Excalidraw (3 endpoints — local file-based)
- `excalidraw.scenes.list` (READ) — list `.excalidraw` files in a configured directory
- `excalidraw.scenes.get` (READ) — read and return scene elements JSON
- `excalidraw.scenes.create` (WRITE) — write new `.excalidraw` JSON file
- Auth: None (local filesystem)
- **Note:** Excalidraw has no public REST API. This plugin manages local `.excalidraw` files. AI generates Excalidraw element JSON → Nexus saves to disk → open in Excalidraw desktop app.
- Config: `nexus.plugins.excalidraw.directory=/path/to/excalidraw/files`

### Miro (5 endpoints + 1 webhook)
- `miro.boards.list` (READ) — `GET /v2/boards`
- `miro.boards.get` (READ) — `GET /v2/boards/{id}`
- `miro.items.list` (READ) — `GET /v2/boards/{id}/items`
- `miro.stickyNotes.create` (WRITE) — `POST /v2/boards/{id}/sticky_notes` (type-specific endpoint)
- `miro.shapes.create` (WRITE) — `POST /v2/boards/{id}/shapes` (type-specific endpoint)
- Webhook: `miro.board.updated`
- Auth: OAuth2 (scopes: `boards:read`, `boards:write`)
- API: `https://api.miro.com/v2` (confirmed, production-ready)
- Rate limit: 100K credits/min (credit-based — headers: `X-RateLimit-Remaining`, `X-RateLimit-Reset`)
- Note: Miro uses **type-specific** creation endpoints (sticky_notes, shapes, cards, connectors), not a generic `/items` POST

---

## 7. Docker Setup

```yaml
# docker-compose.yml
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
      timeout: 5s
      retries: 5
    deploy:
      resources:
        limits:
          memory: 128M

  app:
    build: .
    ports: ["127.0.0.1:8080:8080"]   # localhost only — never expose to network
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/nexus
      SPRING_DATASOURCE_USERNAME: nexus
      SPRING_DATASOURCE_PASSWORD: nexus
      NEXUS_KEK: ${NEXUS_KEK}  # REQUIRED — no default, must be set
      NEXUS_ADMIN_PASSWORD: ${NEXUS_ADMIN_PASSWORD}  # REQUIRED — no default in prod
      NEXUS_PLUGINS_EXCALIDRAW_DIRECTORY: /data/excalidraw
      SPRING_PROFILES_ACTIVE: prod
    volumes:
      - ${EXCALIDRAW_DIR:-./excalidraw-files}:/data/excalidraw  # bind-mount for local .excalidraw files
    depends_on:
      db:
        condition: service_healthy  # waits for pg_isready, prevents startup race
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 30s
    deploy:
      resources:
        limits:
          memory: 256M

volumes:
  pgdata:
```

```dockerfile
# Dockerfile
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN ./gradlew :nexus-app:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache wget && \
    addgroup -S nexus && adduser -S nexus -G nexus
USER nexus
COPY --from=build /app/nexus-app/build/libs/nexus-app*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

---

## 8. Resource Optimization

**`application.yml`** (base):
```yaml
spring:
  threads:
    virtual:
      enabled: true
  datasource:
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
      idle-timeout: 300000
      connection-timeout: 10000
  ai:
    mcp:
      server:
        name: nexus
        version: 1.0.0
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
```

**`application-dev.yml`** (dev profile — lazy init here only, not prod):
```yaml
spring:
  main:
    lazy-initialization: true
logging:
  level:
    dev.nexus: DEBUG
```

**`application-prod.yml`** (prod profile):
```yaml
spring:
  main:
    lazy-initialization: false   # surface bean wiring errors immediately
```

---

## 9. GitHub Actions CI

```yaml
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 25   # LTS version, matches Gradle toolchain and Docker image
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew build
        # Testcontainers works on GitHub-hosted runners (Docker available)
```

---

## 10. Implementation Phases

### Phase 1: Foundation
- **Create GitHub repo** with `CLAUDE.md`, `.gitignore`, Gradle wrapper
- **GitHub Actions CI** (`.github/workflows/ci.yml`)
- Gradle 2-module skeleton with correct SB4 dependencies
- `NexusPlugin` interface, `EndpointNode`, `PluginRegistry`, `PluginContext`, `PluginAuthConfig`, `EndpointSchemas`
- JPA entities (5 classes) + Flyway `V1__init.sql`
- `EncryptionService` (AES-256-GCM + PBKDF2 600K iterations)
- `NexusInitializer` (bootstrap integrations + DEKs + env var credentials)
- Docker Compose (with health check) + Dockerfile
- `SecurityConfig` (endpoint auth rules)
- `CredentialController` (POST /api/credentials/{pluginId})
- Unit tests for `EncryptionService`

### Phase 2: Execution Engine + OAuth
- `EndpointExecutor` with hooks + `@Retryable` (requires `@EnableRetry` + `spring-boot-starter-aop`)
- `PermissionEvaluator` (matrix) + `PermissionEnforcer` (DB-backed)
- `PermissionController` (REST approve/deny)
- `KeyManager` + `KeyManagerFactory`
- `NexusHttpClient` (WebClient + OAuth2 filter + `retryWhen()`)
- OAuth flow via custom `OAuthController` (not Spring Security's default login flow — see note below)
  - `OAuthController` builds auth URLs from plugin `OAuthConfig` + decrypted client creds from DB
  - Callback exchanges code for tokens via WebClient, stores encrypted in `nexus_accounts`
  - Custom `TokenRefreshService` handles token refresh before API calls
  - Custom `ExchangeFilterFunction` on WebClient auto-injects Bearer tokens for OAuth2 plugins
- `InspectService` + `JsonSchemaToTypeString`
- `EventService` (direct DB insert)
- Unit tests for `PermissionEvaluator`, `EndpointExecutor`, `InspectService`

### Phase 3: MCP + Webhooks
- 4 MCP tools via `@McpTool` annotations (SSE transport)
- `WebhookRouter` (two-level matching) + `WebhookController`
- `CleanupScheduler` (`@Scheduled`)
- `PluginHealthIndicator` (Actuator)
- Integration test: MCP tool → endpoint execution → DB verification

### Phase 4: Plugins
- GitHub plugin (3 endpoints + 1 webhook)
- Slack plugin (3 endpoints + 1 webhook)
- Excalidraw plugin (3 endpoints, local file-based)
- Miro plugin (5 endpoints + 1 webhook, OAuth2)
- Integration tests with Testcontainers

### Phase 5: Documentation
- `README.md` (overview, quickstart, Docker setup, MCP config for Claude Code)
- `docs/architecture.md`, `docs/plugin-guide.md`, `docs/mcp-tools.md`
- `docs/configuration.md` (all properties reference)
- `docs/deployment.md` (Docker Compose guide)
- Update `CLAUDE.md` with final architecture

---

## 11. Verification Plan

1. `./gradlew build` — both modules compile, tests pass (CI green)
2. `docker compose up` — PostgreSQL healthy, app boots, Flyway creates tables, plugins bootstrap
3. `GET /actuator/health` → `UP`
4. `POST /api/credentials/github` with `{"api_key":"ghp_xxx"}` → credential stored encrypted
5. MCP: `nexus_setup` → shows GitHub as configured, Slack needs credentials
6. MCP: `list_operations` → returns all plugin endpoints with risk levels
7. MCP: `get_schema` with `github.issues.create` → returns input/output schema
8. MCP: `nexus_run` with `github.repos.list` → returns repos from GitHub API
9. Permission: set mode `STRICT`, `github.issues.create` → "awaiting approval" → `POST /api/permissions/{id}/approve` → re-run succeeds
10. Webhook: POST to `/webhooks/github` with valid signature → event in `nexus_events`
11. Excalidraw: `nexus_run` with `excalidraw.scenes.create` → `.excalidraw` file created in mounted directory
12. Env var creds: set `NEXUS_GITHUB_API_KEY=ghp_xxx` → restart → `nexus_setup` shows GitHub configured
13. Slack: `nexus_run` with `slack.channels.list` → returns channels (after configuring bot token)
14. Miro: complete OAuth2 flow via `/oauth/miro/start` → `nexus_run` with `miro.boards.list` → returns boards

---

## 12. Known Risks & Notes

- **Spring AI MCP** is at 1.0.0 GA but not managed by Spring Boot BOM — version pinned explicitly.
- **springdoc-openapi** deferred — add once Jackson 3 compatible version ships.
- **Excalidraw plugin** is local file-based (no REST API). Functional but different from cloud-based plugins.
- **Miro item creation** uses type-specific endpoints (`/sticky_notes`, `/shapes`), not a generic `/items` POST — plugin must support multiple creation paths.
- **Slack webhook handler** must handle both `url_verification` challenge and `request_signing` verification (HMAC-SHA256, constant-time comparison, 5-minute timestamp freshness check).
