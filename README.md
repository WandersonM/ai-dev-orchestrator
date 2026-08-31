# AI Dev Orchestrator

MVP local de uma software factory baseada em agentes de IA, com roteamento multi-provider entre OpenAI e Google Gemini.

## O que já existe

- WorkItem persistido em PostgreSQL
- Máquina de estados inicial
- Agent abstraction
- Refiner Agent
- LLM Router por tarefa/agente
- OpenAI Gateway via Responses API
- Gemini Gateway via Interactions API
- Auditoria de AgentExecution
- Workspace root controlado
- Command allowlist
- Flyway
- Swagger/OpenAPI
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

Depois execute:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/start
```

O primeiro milestone leva o item de `NEW` para `READY_FOR_DEVELOPMENT` usando o Refiner Agent e a rota `REFINEMENT`.

## Próximo milestone

1. Git worktree por WorkItem
2. Tool registry para read/search/write/run-tests/git-diff
3. Backend Developer Agent em loop tool-calling
4. Reviewer Agent
5. Ciclo `CHANGES_REQUESTED -> IMPLEMENTING -> REVIEWING`
6. GitHub PR
7. Trello webhook/polling
