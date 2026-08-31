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

O Backend Developer Agent trabalha em um `git worktree` isolado por WorkItem e usa tool calling nativo do provider, sem depender de JSON livre gerado em prompt.

## O que já existe

- WorkItem persistido em PostgreSQL
- Máquina de estados de desenvolvimento/review
- Agent abstraction
- Refiner Agent
- Backend Developer Agent
- Reviewer Agent
- LLM Router por tarefa/agente
- OpenAI Gateway via Responses API
- Gemini Gateway via Interactions API
- Function/tool calling nativo em OpenAI e Gemini
- Tool registry
- `search_code`
- `read_file`
- `write_file`
- `run_command`
- Git worktree por WorkItem
- Auditoria de AgentExecution
- Auditoria persistida de cada ToolExecution
- Retomada semântica do Backend Agent usando o histórico persistido
- Workspace root controlado
- Command allowlist
- Limite de passos por agente
- Limite de ciclos de review
- Flyway
- Swagger/OpenAPI
- GitHub Actions com Java 25 + Maven
- Docker Compose apenas para PostgreSQL

## Estratégia padrão de modelos

- Refinement: Gemini (`gemini-3.7-flash`)
- Backend implementation: OpenAI (`gpt-5.6-sol`)
- Review: OpenAI (`gpt-5.6-sol`)

Tudo é configurável por variável de ambiente; nenhum agente conhece diretamente o fornecedor.

## Requisitos

- Java 25
- Maven 3.9+
- Docker / Docker Compose
- API key da OpenAI e/ou Gemini de acordo com as rotas habilitadas
- Git instalado localmente

## Configurar

Copie `.env.example` para `.env` e preencha as chaves. Se iniciar pela IDE, configure as mesmas variáveis no Run Configuration ou exporte-as no shell.

```bash
export OPENAI_API_KEY="..."
export GEMINI_API_KEY="..."
```

Você pode trocar qualquer rota sem recompilar:

```bash
export AIDEV_REFINEMENT_PROVIDER=OPENAI
export AIDEV_REFINEMENT_MODEL=gpt-5.6-sol

export AIDEV_BACKEND_PROVIDER=GEMINI
export AIDEV_BACKEND_MODEL=gemini-3.7-flash
```

Limites dos agentes:

```bash
export AIDEV_BACKEND_MAX_STEPS=20
export AIDEV_REVIEW_MAX_ITERATIONS=3
```

## Executar

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Swagger: `http://localhost:8080/swagger-ui.html`

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

Avance o workflow chamando o mesmo endpoint enquanto houver uma transição automática disponível:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/start
```

Consulte o item:

```bash
curl http://localhost:8080/api/work-items/{id}
```

Consulte todas as ferramentas executadas pelo agente, incluindo argumentos, saída, erro e duração:

```bash
curl http://localhost:8080/api/work-items/{id}/tool-executions
```

## Segurança local

Os agentes não executam shell arbitrário. O `LocalCommandExecutor` valida o diretório de trabalho contra o workspace root e o `CommandPolicy` aplica uma allowlist de executáveis. O `.env` e arquivos locais sensíveis ficam fora do Git.

Os gateways usam estado de continuação do provider durante uma execução com ferramentas (`previous_response_id` na OpenAI e `previous_interaction_id` no Gemini). O histórico operacional necessário para retomar uma execução local também é persistido no PostgreSQL.

## Próximos milestones

1. Publicação controlada da branch e criação de Draft PR no GitHub
2. Persistência de métricas de tokens/custo por AgentExecution
3. Trello adapter para buscar/refinar cards e atualizar status
4. Frontend Developer Agent
5. QA Agent
6. Security Reviewer
7. Risk classification e human gates por criticidade
