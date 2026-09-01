# Local development with IntelliJ

The recommended local setup is the `local` Spring profile. It keeps the committed defaults safe and imports an external file at `./config/application-local.yml` for machine-specific database credentials and provider access.

## 1. Requirements

- JDK 25
- Maven 3.9+
- PostgreSQL 17+ (or compatible PostgreSQL)
- Git
- Node only if repositories handled by agents need Node tooling
- Codex CLI only if you want to delegate selected roles to ChatGPT Codex

## 2. Open in IntelliJ

Open the repository root as a Maven project. The repository includes the shared run configuration:

`AI Dev Orchestrator - Local`

It starts `com.ordevia.aidev.AiDevOrchestratorApplication` with the Spring profile `local`.

## 3. Configure your database

Copy the template:

```bash
mkdir -p config
cp config/application-local.example.yml config/application-local.yml
```

Then edit `config/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aidev
    username: aidev
    password: your-password
  flyway:
    enabled: true
    clean-disabled: true
    validate-on-migrate: true
```

`config/application-local.yml` is ignored by Git.

When the application starts with profile `local`, Flyway validates and applies all pending migrations automatically before Hibernate validates the schema.

You can also start once with the default database, open the Control Plane and configure the same file from **Acessos & Models**. Changes written through the UI require an application restart because Spring configuration properties are bound during startup.

## 4. Start

From IntelliJ, select `AI Dev Orchestrator - Local` and click Run.

Or from a terminal:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open:

- Control Plane: `http://127.0.0.1:8080/`
- Swagger: `http://127.0.0.1:8080/swagger-ui.html`

## 5. Configure access from the UI

The **Acessos & Models** screen can write the external local YAML with:

- database URL/user/password
- OpenAI API key and base URL
- Gemini API key and base URL
- GitHub token and Draft PR publication flag
- Trello credentials
- MCP enabled flag
- Codex CLI enablement, binary, model override and delegated roles
- model/provider routing per delivery role

Secrets are write-only in the UI. The API returns only `configured=true/false`, never the secret value.

On POSIX filesystems the generated local YAML is set to owner read/write permissions (`0600`).

## 6. Codex Pro

Install and authenticate the official Codex CLI on the same machine running the orchestrator:

```bash
npm install -g @openai/codex
codex login
codex login status
```

Then enable Codex in **Acessos & Models** and choose the roles you want delegated. The orchestrator does not persist the ChatGPT OAuth token; the child `codex` process uses the CLI's own authenticated session.

## 7. First product test

A good first test is:

1. Create a project in **Projetos & Repos**.
2. Point a repository profile at a small Git repository.
3. Create a WorkItem.
4. Open **Planejamento** and start the Product Planning Agent.
5. Answer blocking questions in the UI.
6. Approve the specification.
7. Use **Executar próxima onda** to continue the pipeline.
8. Watch sessions under **Agentes** and send live guidance when needed.
9. Add stable domain knowledge under the project's **Knowledge Base**.
10. Review approvals and eventually publish the coordinated Draft PRs.

For a fully disposable smoke test, use `scripts/dogfood_e2e.py`.
