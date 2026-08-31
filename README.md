# AI Dev Orchestrator

Software factory local baseada em agentes de IA, com OpenAI/Gemini, tool calling, MCP, políticas por papel, planejamento humano interativo e orquestração de projetos em DAG.

## Fluxo completo

```text
IDEIA / CARD
    |
    v
Product Planning Agent
    |
    +--> perguntas de negócio --> HUMANO --> nova rodada
    |
    +--> revisão do plano ------> HUMANO --> aprova ou pede mudanças
    |
    v
Domain Guardian
    |
    v
Architect Agent
    |  escolhe DELIVERY_ROLES
    +-----------------------+
    |                       |
    v                       v
Backend Agent          Frontend Agent
    |                       |
    +-----------+-----------+
                |
                v
        Integration Agent (quando necessário)
                |
                v
             QA Agent
                |
                v
          Reviewer Agent
                |
                v
       Security Reviewer
                |
                v
        Release Readiness
                |
                v
            HUMAN GATE
                |
              merge
                |
                v
              DONE
                |
          desbloqueia DAG
```

Projetos agrupam WorkItems e dependências `blockedBy`. O orchestrator calcula ondas topológicas e executa em paralelo apenas itens cujos blockers estejam `DONE`.

## Planejamento interativo

O Product Planning Agent não pode preencher lacunas relevantes com suposições silenciosas. Quando uma informação ausente puder alterar regra de negócio, dados, API/contrato, cobrança, integração, segurança, permissão, experiência do usuário, auditoria ou operação, ele deve perguntar.

A sessão diferencia:

- `facts`: informação presente no card ou confirmada;
- `decisions`: decisão humana explícita;
- `assumptions`: hipótese ainda não confirmada;
- perguntas `blocking`: impedem o planejamento de ser considerado pronto.

Ele faz no máximo cinco perguntas prioritárias por rodada e possui um limite configurável de rodadas. Ao atingir o limite, o item vai para `PLANNING_HUMAN_REQUIRED` em vez de continuar perguntando indefinidamente.

Estados principais:

```text
NEW
 -> PLANNING
 -> WAITING_FOR_USER_INPUT
 -> PLANNING
 -> READY_FOR_PLANNING_REVIEW
 -> READY_FOR_DOMAIN_VALIDATION (após aprovação humana)
```

Se a especificação pronta ainda não estiver boa, o humano pode pedir mudanças com feedback livre e iniciar outra rodada sem perder o histórico.

### Exemplo

Inicie o planejamento:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/planning/start
```

Consulte a sessão e perguntas:

```bash
curl http://localhost:8080/api/work-items/{id}/planning
curl http://localhost:8080/api/work-items/{id}/planning/questions
```

Responda uma pergunta:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/planning/questions/{questionId}/answer \
  -H 'Content-Type: application/json' \
  -d '{
    "answer":"Somente títulos ABERTO e VENCIDO podem ser alterados.",
    "answeredBy":"wanderson"
  }'
```

Depois de responder todas as perguntas bloqueantes da rodada:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/planning/continue
```

Quando estiver em `READY_FOR_PLANNING_REVIEW`, aprove:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/planning/approve
```

Ou peça revisão:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/planning/request-changes \
  -H 'Content-Type: application/json' \
  -d '{
    "feedback":"Faltou considerar títulos registrados no banco e a auditoria da data anterior.",
    "providedBy":"wanderson"
  }'
```

Histórico de feedback:

```bash
curl http://localhost:8080/api/work-items/{id}/planning/feedback
```

## Papéis

| Papel | Responsabilidade | Escrita no código |
|---|---|---|
| Product Planning / REFINER | descobrir requisitos e conversar com o humano | não |
| DOMAIN_GUARDIAN | validar terminologia e invariantes do domínio | não |
| ARCHITECT | inspecionar o repo e produzir plano técnico/roles | não |
| BACKEND_DEVELOPER | backend, domínio, API, dados | sim |
| FRONTEND_DEVELOPER | UI/client quando necessário | sim |
| INTEGRATION_ENGINEER | corrigir seams/contratos entre componentes | sim, restrito |
| QA_ENGINEER | testes e validação automatizada | sim, focado em testes |
| REVIEWER | code review contra spec + arquitetura | não |
| SECURITY_REVIEWER | revisão de AppSec | não |
| RELEASE_ENGINEER | readiness, release notes e rollback checklist | não |

O Architect seleciona somente os implementadores necessários usando um contrato como:

```text
DELIVERY_ROLES: BACKEND,FRONTEND,INTEGRATION
DECISION: READY
```

Assim um card puramente backend não paga por um agente frontend sem necessidade.

## Human gates

O sistema prefere pedir decisão humana a inventar contexto. Além do planejamento, há gates para domínio, arquitetura e release quando os agentes não têm evidência suficiente:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/human-gates/domain/approve
curl -X POST http://localhost:8080/api/work-items/{id}/human-gates/architecture/approve
curl -X POST http://localhost:8080/api/work-items/{id}/human-gates/release/approve
```

O merge final continua humano. Depois do merge:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/complete
```

Somente `DONE` satisfaz `blockedBy` e libera a próxima onda.

## Projetos, DAG e ondas

```text
A ---------> B -----> D
 \--------> C -----/

Wave 1: A
Wave 2: B + C
Wave 3: D
```

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
    "repositoryPath":"repositories/erp"
  }'
```

Adicionar WorkItem:

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

Consultar DAG e fronteira executável:

```bash
curl http://localhost:8080/api/projects/{projectId}/dag
```

Executar a próxima fronteira até cada item chegar a algum human gate, `DONE` ou `FAILED`:

```bash
curl -X POST http://localhost:8080/api/projects/{projectId}/execute-ready
```

Auditoria das ondas:

```bash
curl http://localhost:8080/api/projects/{projectId}/wave-executions
curl http://localhost:8080/api/projects/{projectId}/wave-executions/{waveExecutionId}/items
```

## Worktrees

Cada WorkItem usa `git worktree` isolado. Se o repositório tiver `origin`, a nova branch nasce de `origin/<base-branch>` após fetch; se for um sandbox puramente local, nasce do `HEAD`.

```text
workspace/
  repositories/erp
  worktrees/CARD-101
  worktrees/CARD-102
```

Isso permite múltiplos agentes atuando em paralelo sem compartilhar checkout.

## Tool calling e MCP

Todos os agentes que precisam de ferramentas passam pelo mesmo `ToolLoopRunner` e `ToolRegistry`.

Tools locais atuais:

```text
search_code
read_file
write_file
run_command
```

MCP suporta STDIO e Streamable HTTP. Discovery remoto via `tools/list` registra automaticamente ferramentas como:

```text
mcp_context7_search_docs
mcp_postgres_describe_table
mcp_github_search_issue
```

MCP vem desligado por padrão:

```bash
export AIDEV_MCP_ENABLED=true
export AIDEV_MCP_REQUEST_TIMEOUT=30s
```

Exemplo `application-local.yml`:

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

Status/reconnect:

```bash
curl http://localhost:8080/api/mcp/servers
curl -X POST http://localhost:8080/api/mcp/servers/docs/reconnect
```

## ToolPolicy

A política é deny-by-default e aplicada tanto na exposição ao LLM quanto na execução.

Padrão atual:

- Product Planning: sem tools;
- Domain Guardian: read-only;
- Architect: read-only;
- Backend/Frontend/Integration/QA: tools de escrita/execução autorizadas;
- Reviewer/Security/Release: read-only.

MCPs não ficam autorizados apenas por estarem conectados. Libere explicitamente por papel, por exemplo:

```yaml
aidev:
  tool-policy:
    policies:
      ARCHITECT:
        allow:
          - search_code
          - read_file
          - mcp_context7_*
      BACKEND_DEVELOPER:
        allow:
          - search_code
          - read_file
          - write_file
          - run_command
          - mcp_context7_*
          - mcp_postgres_describe_*
```

Auditoria:

```bash
curl http://localhost:8080/api/tool-policies
curl http://localhost:8080/api/tool-policies/BACKEND_DEVELOPER
```

## LLM routing

Cada papel pode usar provider/modelo diferente sem recompilar. Defaults atuais:

```text
Planning          Gemini
Domain Guardian   Gemini
Architecture      OpenAI
Backend           OpenAI
Frontend          Gemini
Integration       OpenAI
QA                Gemini
Review            OpenAI
Security          OpenAI
Release           OpenAI
```

Veja `.env.example` para todas as variáveis.

## Auditoria

Persistimos:

- AgentExecution;
- ToolExecution com argumentos, saída, erro, duração e step;
- PlanningSession, perguntas, respostas e feedback humano;
- especificação aprovada;
- validação de domínio;
- architecture plan + delivery roles;
- relatórios de implementação, integração, QA, review, security e release;
- WaveExecution + itens da onda;
- branch/PR metadata.

Tool history:

```bash
curl http://localhost:8080/api/work-items/{id}/tool-executions
```

## GitHub Draft PR

Publicação continua explícita e desligada por padrão:

```bash
export AIDEV_GITHUB_PUBLISH_ENABLED=true
export AIDEV_GITHUB_TOKEN=...
```

Quando o item chegar a `READY_FOR_HUMAN_REVIEW`:

```bash
curl -X POST http://localhost:8080/api/work-items/{id}/publish
```

O sistema cria/usa a branch do WorkItem e prepara um Draft PR; merge e produção permanecem gates humanos.

## Segurança e governança

- `.env` e `application-local.yml` ficam fora do Git;
- workspace root restringe filesystem;
- `CommandPolicy` restringe executáveis do shell;
- `ToolPolicy` é deny-by-default;
- MCP fica desligado por padrão e requer allowlist por agente;
- Reviewer, Security, Domain Guardian e Release são read-only por padrão;
- dependências não atravessam Projects;
- ciclos no DAG são rejeitados;
- WorkItem usa optimistic locking;
- planejamento não pode ficar READY com pergunta ou assumption bloqueante;
- somente `DONE` libera dependentes;
- Release Agent não recebe permissão para deploy/tag/push;
- publicação GitHub requer habilitação explícita.

## Requisitos e execução

- Java 25
- Maven 3.9+
- Docker / Docker Compose
- Git
- API key da OpenAI e/ou Gemini conforme rotas habilitadas
- runtime necessário aos MCPs STDIO usados

```bash
docker compose up -d postgres
mvn spring-boot:run
```

Swagger: `http://localhost:8080/swagger-ui.html`

## Próximos passos

- Trello adapter para importar cards, comentários/respostas e `blockedBy`;
- classificação de risco/capabilities para ferramentas sensíveis;
- tokens/custo/latência por agente, WorkItem e onda;
- contracts/multi-repository para backend + frontend em repositórios separados;
- JetBrains bridge opcional para symbol index, inspections e refactors;
- webhook de merge para marcar `DONE` automaticamente sob política.
