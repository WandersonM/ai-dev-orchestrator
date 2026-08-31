# AI Dev Orchestrator

MVP local de uma software factory baseada em agentes de IA, com roteamento multi-provider entre OpenAI e Google Gemini.

## Estado atual

Já existe um fluxo funcional de refinamento, implementação e review:

```text
NEW
 -> REFINING
 -> READY_FOR_DEVELOPMENT
 -> IMPLEMENTING
 -> REVIEWING
 -> CHANGES_REQUESTED | READY_FOR_HUMAN_REVIEW
```

O Backend Developer Agent trabalha em um `git worktree` isolado por WorkItem e usa tool calling nativo do provider. Tools locais e tools descobertas via MCP entram no mesmo `ToolRegistry`.

## O que já existe

- WorkItem persistido em PostgreSQL
- Máquina de estados de desenvolvimento/review
- Refiner Agent, Backend Developer Agent e Reviewer Agent
- LLM Router por tarefa/agente
- OpenAI Responses API + native function calling
- Gemini Interactions API + native function calling
- Tool registry dinâmico
- Tools locais: `search_code`, `read_file`, `write_file`, `run_command`
- MCP Java SDK 2.x
- MCP client STDIO
- MCP client Streamable HTTP
- Discovery automático via `tools/list`
- MCP tools convertidas automaticamente para `AgentTool`
- Namespace de tools MCP: `mcp_<server>_<tool>`
- Filtro `include-tools` / `exclude-tools` por servidor
- Reconnect e status de servidores MCP via REST
- Git worktree por WorkItem
- Auditoria de AgentExecution e ToolExecution
- Retomada semântica usando histórico persistido
- Workspace root controlado e command allowlist
- Limite de passos e ciclos de review
- Publicação explícita para Draft PR no GitHub, desabilitada por padrão
- Flyway, Swagger/OpenAPI e GitHub Actions Java 25

## Requisitos

- Java 25
- Maven 3.9+
- Docker / Docker Compose
- Git
- API key OpenAI e/ou Gemini para as rotas habilitadas
- Runtime necessário aos MCPs STDIO usados, por exemplo Node/npm, Python/uv ou Java

## Executar

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Swagger: `http://localhost:8080/swagger-ui.html`

## MCP

MCP vem desabilitado por padrão:

```bash
export AIDEV_MCP_ENABLED=true
export AIDEV_MCP_REQUEST_TIMEOUT=30s
```

Os servidores são definidos em `application-local.yml` ou outro profile externo. Não coloque tokens reais em arquivo versionado.

### Exemplo STDIO

```yaml
aidev:
  mcp:
    enabled: true
    servers:
      filesystem:
        enabled: true
        transport: STDIO
        command: npx
        args:
          - -y
          - "@modelcontextprotocol/server-filesystem"
          - "/caminho/permitido"
        include-tools:
          - read_file
          - list_directory
```

Variáveis específicas do processo podem ser passadas explicitamente:

```yaml
aidev:
  mcp:
    servers:
      example:
        transport: STDIO
        command: npx
        args: ["-y", "@vendor/server"]
        env:
          API_TOKEN: ${EXAMPLE_MCP_TOKEN}
```

O SDK STDIO herda apenas um conjunto restrito de variáveis de ambiente do SO e acrescenta as declaradas em `env`; não repasse chaves desnecessárias para MCPs de terceiros.

### Exemplo Streamable HTTP

```yaml
aidev:
  mcp:
    enabled: true
    servers:
      docs:
        enabled: true
        transport: STREAMABLE_HTTP
        url: https://example.com
        endpoint: /mcp
        headers:
          Authorization: "Bearer ${DOCS_MCP_TOKEN}"
        exclude-tools:
          - dangerous_delete
```

Após o bootstrap, o orchestrator executa `initialize` + `tools/list`. Uma tool remota como `search_docs` no servidor `docs` é exposta ao agente como:

```text
mcp_docs_search_docs
```

O JSON Schema informado pelo MCP é repassado para o function calling nativo de OpenAI/Gemini.

### Operação MCP

```bash
# status dos servidores
curl http://localhost:8080/api/mcp/servers

# reconectar e refazer discovery de tools
curl -X POST http://localhost:8080/api/mcp/servers/docs/reconnect
```

Falha em um servidor MCP não impede o orchestrator de iniciar; o servidor fica com status `FAILED` e pode ser reconectado depois.

## Criar um WorkItem

```bash
curl -X POST http://localhost:8080/api/work-items \
  -H 'Content-Type: application/json' \
  -d '{
    "externalId":"TEST-001",
    "title":"Criar endpoint de health check",
    "description":"Criar um endpoint que informe o status da aplicação.",
    "repositoryPath":"repositories/sample"
  }'
```

Avance o workflow:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/start
```

Auditoria das ferramentas:

```bash
curl http://localhost:8080/api/work-items/{id}/tool-executions
```

## Segurança

- `.env` e `application-local.yml` não devem ser versionados.
- Shell local passa por `CommandPolicy` e workspace root.
- MCP fica desabilitado por padrão.
- Tools MCP podem ser limitadas com `include-tools` e `exclude-tools`.
- Use credenciais de menor privilégio possível em MCPs externos.
- Publicação GitHub exige habilitação explícita e só ocorre em `READY_FOR_HUMAN_REVIEW`.

## Próximos milestones

1. ToolPolicy por AgentType e classificação de risco para MCPs
2. Persistência de métricas de tokens/custo por AgentExecution
3. Trello adapter
4. Frontend Developer Agent
5. QA Agent
6. Security Reviewer
7. JetBrains bridge opcional para índices, inspections e refactors
