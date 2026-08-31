# AI Dev Orchestrator

MVP local de uma software factory baseada em agentes de IA, com OpenAI/Gemini, tool calling, MCP, políticas por agente e orquestração de projetos em DAG.

## Estado atual

Um WorkItem individual percorre:

```text
NEW
 -> REFINING
 -> READY_FOR_DEVELOPMENT
 -> IMPLEMENTING
 -> REVIEWING
 -> CHANGES_REQUESTED | READY_FOR_HUMAN_REVIEW
 -> DONE (gate humano)
```

Projetos agrupam WorkItems e dependências `blockedBy`. O orchestrator calcula ondas topológicas e executa, em paralelo, apenas itens cujos blockers estejam `DONE`.

```text
A ---------> B -----> D
 \--------> C -----/

Wave 1: A
Wave 2: B + C
Wave 3: D
```

Cada WorkItem usa um `git worktree` isolado. Se o repositório tem `origin`, uma nova worktree nasce de `origin/<base-branch>` atualizado; em um sandbox puramente local, ela nasce do `HEAD`.

## Principais capacidades

- Project + WorkItem persistidos em PostgreSQL
- Dependências `blockedBy` persistidas e validadas
- Detecção de ciclos no DAG
- Planejamento de ondas topológicas
- Execução paralela com virtual threads e limite configurável
- Auditoria persistida de WaveExecution e itens da onda
- `DONE` como condição de desbloqueio de dependências
- Optimistic locking em WorkItem
- Refiner Agent, Backend Developer Agent e Reviewer Agent
- OpenAI Responses API + native function calling
- Gemini Interactions API + native function calling
- Tool registry dinâmico e ToolPolicy deny-by-default
- Tools locais: `search_code`, `read_file`, `write_file`, `run_command`
- MCP STDIO + Streamable HTTP + discovery `tools/list`
- MCP tools adaptadas automaticamente para AgentTool
- Auditoria de AgentExecution e ToolExecution
- Git worktree por WorkItem
- Publicação explícita para Draft PR no GitHub
- Flyway, Swagger/OpenAPI e GitHub Actions Java 25

## Requisitos

- Java 25
- Maven 3.9+
- Docker / Docker Compose
- Git
- API key OpenAI e/ou Gemini para as rotas habilitadas
- Runtime dos MCPs STDIO usados, por exemplo Node/npm, Python/uv ou Java

## Executar

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Swagger: `http://localhost:8080/swagger-ui.html`

## Orquestração por projeto

Paralelismo máximo:

```bash
export AIDEV_MAX_PARALLEL=3
```

Criar projeto:

```bash
curl -X POST http://localhost:8080/api/projects \
  -H 'Content-Type: application/json' \
  -d '{
    "name":"Financeiro ERP",
    "description":"Nova jornada financeira",
    "repositoryPath":"repositories/erp-backend"
  }'
```

Criar o primeiro ticket:

```bash
curl -X POST http://localhost:8080/api/projects/{projectId}/work-items \
  -H 'Content-Type: application/json' \
  -d '{
    "externalId":"CARD-101",
    "title":"Criar entidade Conta Bancária",
    "description":"...",
    "blockedBy":[]
  }'
```

Criar um ticket dependente:

```bash
curl -X POST http://localhost:8080/api/projects/{projectId}/work-items \
  -H 'Content-Type: application/json' \
  -d '{
    "externalId":"CARD-102",
    "title":"Criar API de contas",
    "description":"...",
    "blockedBy":["{workItemIdCard101}"]
  }'
```

Consultar o DAG, ondas, ciclos e itens executáveis agora:

```bash
curl http://localhost:8080/api/projects/{projectId}/dag
```

Executar em paralelo toda a fronteira liberada do DAG até cada item chegar a um gate:

```bash
curl -X POST http://localhost:8080/api/projects/{projectId}/execute-ready
```

A resposta inclui `waveExecutionId`. O histórico fica persistido:

```bash
curl http://localhost:8080/api/projects/{projectId}/wave-executions
curl http://localhost:8080/api/projects/{projectId}/wave-executions/{waveExecutionId}/items
```

A onda termina como `COMPLETED`, `PARTIAL_FAILURE` ou `FAILED` e mantém status antes/depois e erro por WorkItem.

Após review/merge humano de um item em `READY_FOR_HUMAN_REVIEW`, marque-o concluído:

```bash
curl -X POST http://localhost:8080/api/work-items/{workItemId}/complete
```

Somente `DONE` satisfaz `blockedBy`; portanto a próxima onda não começa apenas porque a IA terminou ou abriu um PR.

## Tool Policy

A política é deny-by-default e aplicada duas vezes: antes da tool ser exposta ao LLM e antes da execução.

```yaml
aidev:
  tool-policy:
    default-effect: DENY
    policies:
      BACKEND_DEVELOPER:
        allow:
          - search_code
          - read_file
          - write_file
          - run_command
          - mcp_context7_*
      REVIEWER:
        allow:
          - search_code
          - read_file
        deny:
          - write_file
          - run_command
      REFINER:
        allow: []
        deny:
          - '*'
```

`deny` sempre vence `allow` e padrões aceitam wildcard `*`.

Auditoria:

```bash
curl http://localhost:8080/api/tool-policies
curl http://localhost:8080/api/tool-policies/BACKEND_DEVELOPER
```

## MCP

MCP vem desabilitado por padrão:

```bash
export AIDEV_MCP_ENABLED=true
export AIDEV_MCP_REQUEST_TIMEOUT=30s
```

Exemplo STDIO em `application-local.yml`:

```yaml
aidev:
  mcp:
    enabled: true
    servers:
      filesystem:
        transport: STDIO
        command: npx
        args: ["-y", "@modelcontextprotocol/server-filesystem", "/caminho/permitido"]
        include-tools: [read_file, list_directory]
```

Exemplo Streamable HTTP:

```yaml
aidev:
  mcp:
    enabled: true
    servers:
      docs:
        transport: STREAMABLE_HTTP
        url: https://example.com
        endpoint: /mcp
        headers:
          Authorization: "Bearer ${DOCS_MCP_TOKEN}"
```

Operação:

```bash
curl http://localhost:8080/api/mcp/servers
curl -X POST http://localhost:8080/api/mcp/servers/docs/reconnect
```

## WorkItem individual

Ainda é possível usar o modo simples sem Project:

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

Avançar uma transição:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/start
```

Auditoria das tools:

```bash
curl http://localhost:8080/api/work-items/{id}/tool-executions
```

## Segurança e governança

- `.env` e `application-local.yml` não devem ser versionados.
- Shell local passa por CommandPolicy e workspace root.
- ToolPolicy é deny-by-default.
- MCP fica desabilitado por padrão.
- Dependências não atravessam Projects e ciclos são rejeitados.
- WorkItem usa optimistic locking contra processamento concorrente acidental.
- Colisão de versão/estado retorna HTTP 409; DAG inválido retorna 400.
- Uma dependência só é liberada por `DONE`, que representa o gate humano concluído.
- Repositórios com remoto criam novas worktrees sobre `origin/<base-branch>` atualizado.
- Publicação GitHub exige habilitação explícita.

## Próximos milestones

1. Classificação de risco/capabilities e aprovação humana para tools sensíveis
2. Persistir tokens/custo/latência por agente e por onda
3. Trello adapter para importar cards e `blockedBy`
4. Frontend Developer Agent
5. QA Agent
6. Security Reviewer
7. JetBrains bridge opcional
