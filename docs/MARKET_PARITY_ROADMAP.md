# AI Dev Orchestrator — Market Parity & Product Roadmap

## Product thesis

The orchestrator is an agent control plane for software delivery: product discovery, planning, domain validation, architecture, multi-repository implementation, integration, QA, adversarial critique, review, security, release readiness and human approvals.

It intentionally supports legacy and modern repositories in the same installation without sharing runtime, branch or architecture assumptions.

## V1 operational baseline — COMPLETE

The v1 baseline is complete when a task can travel from card/API input to coordinated PRs with explicit human gates, auditable agent/tool activity and isolated execution. The current implementation includes:

- interactive Product Planning with blocking questions and human review;
- Domain Guardian, Architect, Backend, Frontend, Integration, QA, Critic, Reviewer, Security and Release roles;
- dynamic project DAG and parallel wave execution;
- multi-repository and multi-branch worktrees;
- OpenAI/Gemini routing, telemetry, budgets, fallback and circuit breaker;
- MCP tools plus deny-by-default role policy and risk approvals;
- AgentSession lifecycle, pause/resume/cancel, live human messages, checkpoints and forks;
- LOCAL_WORKTREE, DOCKER and SELF_HOSTED_WORKER execution backends;
- repository environment and verification profiles;
- AGENTS.md/path-scoped instructions, SKILL.md registry, codebase map cache and durable project knowledge;
- Trello import/planning feedback and GitHub review webhook ingestion;
- coordinated draft PR publication and per-repository GitHub CI/merge readiness aggregation;
- immutable audit events and proof-of-work Artifact Registry;
- local control-plane UI;
- project delivery analytics for cost, tokens, latency, human intervention, verification failures, first-pass review, waves and cycle time.

## Definition of complete

### 1. Multi-repository & multi-branch — COMPLETE
- [x] Repository profiles per Project
- [x] WorkItem-to-repository bindings
- [x] Per-repository base branch and branch prefix
- [x] Per-WorkItem base branch override
- [x] Multi-root worktree workspace
- [x] Coordinated multi-repository diff
- [x] Versioned repository instructions in agent context
- [x] Coordinated Draft PR set per WorkItem
- [x] Cross-repository merge readiness and dependency ordering
- [x] Per-repository CI status aggregation

### 2. Execution environments — COMPLETE
- [x] ExecutionBackend abstraction
- [x] LOCAL_WORKTREE backend
- [x] DOCKER sandbox backend
- [x] SELF_HOSTED_WORKER backend
- [x] environment profile as code
- [x] setup/install hooks
- [x] CPU/memory/PID/time quotas for sandboxed execution
- [x] secret/environment allowlists and redaction
- [x] network deny-by-default policy for Docker sandbox
- [x] configurable deny-by-default command allowlist
- [x] bounded, concurrently drained process output

### 3. Instructions, skills and context — COMPLETE
- [x] AGENTS.md/.ai instruction loading
- [x] nested/path-scoped instructions
- [x] Skills registry (SKILL.md)
- [x] skill tool prerequisites
- [x] skill enablement by agent role and available tools
- [x] architecture/codebase map cache
- [x] bounded/condensed context inputs for long-running agents
- [x] durable project/domain knowledge with provenance and supersede history

### 4. Agent sessions and collaboration — COMPLETE
- [x] persisted planning conversation
- [x] questions/answers/feedback
- [x] persisted tool executions
- [x] explicit AgentSession aggregate
- [x] pause/resume/cancel
- [x] real-time human message injection at agent safe points
- [x] retry from checkpoint
- [x] branch/fork an agent session with workspace snapshot lineage
- [x] sub-agent delegation
- [x] independent Critic sub-agent before final Reviewer decision

### 5. Verification and proof of work — COMPLETE FOR V1
- [x] QA Agent
- [x] Reviewer Agent
- [x] Security Reviewer
- [x] repository-defined verification matrix
- [x] build/test/lint/static-analysis/coverage command gates
- [x] migration/schema command checks
- [x] contract command checks across repository profiles
- [x] proof-of-work Artifact Registry for logs/reports/screenshots/videos
- [ ] browser/computer-use provider — optional extension
- [ ] JetBrains semantic index/debugger provider — optional MCP/IDE extension

### 6. Governance — COMPLETE
- [x] deny-by-default ToolPolicy
- [x] human gates
- [x] tool capabilities (READ/WRITE/EXECUTE/NETWORK/DB/GIT/PROD)
- [x] risk level per tool call
- [x] policy-driven approval requests
- [x] autonomy levels
- [x] cost/token/time budgets
- [x] model fallback/circuit breakers
- [x] secret scanning/redaction
- [x] immutable audit/event log
- [x] local-by-default control plane and optional bearer control token

### 7. Telemetry and economics — COMPLETE FOR V1
- [x] provider/model per LLM call
- [x] input/output/cached tokens
- [x] estimated cost
- [x] LLM latency
- [x] cost/tokens per WorkItem and Project
- [x] wave execution duration/failure metrics
- [x] first-pass review rate
- [x] human intervention rate
- [x] verification/rework indicators
- [x] cycle time to publication
- [x] model usage comparison by provider/model
- [ ] provider-billed actual cost reconciliation — optional provider-specific extension

### 8. Integrations — COMPLETE FOR V1
- [x] Trello card -> WorkItem import
- [x] Planning questions as Trello comments
- [x] human answers/approval/change requests from Trello
- [x] Trello webhook trigger plus polling fallback
- [x] GitHub Draft PR publishing
- [x] GitHub review/comment feedback ingestion
- [x] GitHub CI/check aggregation
- [x] signed GitHub webhook
- [ ] Slack/Teams adapter — optional extension

### 9. Control-plane UI — COMPLETE FOR V1
- [x] Project dashboard
- [x] DAG/waves visualization
- [x] live agent sessions and controls
- [x] approval inbox
- [x] selected WorkItem telemetry/budget/audit
- [x] MCP server visibility
- [x] control-token support
- [ ] richer multi-repo diff/PR explorer — product enhancement
- [ ] embedded artifact gallery/player — product enhancement
- [ ] advanced analytics charts — product enhancement

## Next product phase — dogfooding and reliability

The next phase is not another agent role. It is proving the platform on real work and hardening the feedback loop:

1. run a controlled end-to-end task through planning -> implementation -> verification -> PR -> GitHub review -> DONE;
2. run the same flow on a legacy repository with its own branch/runtime profile;
3. collect delivery analytics and compare provider/model quality and cost;
4. convert failures discovered during dogfooding into regression tests;
5. add browser/IDE providers only where real tasks demonstrate a need.

## Product principles

1. **Human uncertainty is a state, not a prompt failure.** Agents stop and ask when business truth is missing.
2. **Repository boundaries are first-class.** A legacy repository never silently inherits modern-project conventions.
3. **Execution is isolated.** Parallel agents never share a mutable checkout.
4. **No implicit production access.** Production/network/database mutation requires explicit capability and policy.
5. **Evidence before approval.** Tests, diffs, CI, artifacts and reviews are part of the result.
6. **Models are replaceable.** Agent roles depend on capabilities/tasks, not provider names.
7. **Everything important is auditable.** Human decisions, model calls, tool calls, policies and PRs have provenance.
8. **Cost is an engineering metric.** A feature is not complete operationally until cost, duration and intervention are observable.
