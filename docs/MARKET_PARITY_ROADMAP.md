# AI Dev Orchestrator — Market Parity & Product Roadmap

## Product thesis

The orchestrator is not another chat-based coding assistant. It is an agent control plane for software delivery: product discovery, planning, domain validation, architecture, multi-repository implementation, integration, QA, review, security, release readiness and human approvals.

The design intentionally supports two extremes in the same installation:

- legacy repositories with old runtimes, unusual build commands, long-lived branches and fragile integration constraints;
- modern repositories with current Java/Node stacks, fast CI, typed contracts and trunk/main-oriented delivery.

No repository is allowed to inherit runtime, branching or architectural assumptions from another repository.

## Market patterns adopted

### JetBrains Junie / AI Assistant

Adopt:
- plan before code;
- IDE semantic index/debugger/database as optional tools through MCP/IDE bridge;
- AGENTS.md/project instructions;
- MCP extensibility;
- model/provider flexibility;
- human confirmation before high-impact execution.

### Cursor Agents

Adopt:
- isolated git worktrees;
- parallel sessions;
- multi-root/multi-repository task workspaces;
- environment configuration as code;
- background/long-running execution;
- artifacts as proof of work;
- sandboxed/self-hosted execution backends;
- branch-first workflow where PR creation is explicit rather than mandatory.

### OpenHands / Agent Canvas

Adopt:
- visual control plane concept;
- parallel agent sessions;
- planning mode that asks clarifying questions;
- sub-agent delegation;
- critic/verifier role;
- pluggable execution backend;
- self-hosting.

### GitHub Copilot Agents

Adopt:
- issue/task to branch to PR lifecycle;
- ephemeral/firewalled execution option;
- explicit permission scopes;
- custom agents;
- MCP integration;
- lifecycle hooks/session management;
- review feedback loop.

### Factory Software Factory

Adopt:
- SDLC-wide agents rather than code-only agents;
- model routing by task;
- external signal ingestion;
- measurable/auditable workflows;
- reusable automations and missions/epics;
- organization-level governance.

## Target architecture

```text
Signals
  Trello / GitHub / API / future Slack
        |
        v
Product Planning + Human Conversation
        |
        v
Domain Guardian
        |
        v
Architecture + Dynamic DAG
        |
        v
Mission / WorkItems / Waves
        |
        +-------------------------------+
        |                               |
        v                               v
Multi-root Workspace              Agent Control Plane
repo A / branch X                 policies / budgets / approvals
repo B / branch Y                 sessions / audit / telemetry
repo C / branch Z                 tools / MCP / skills
        |                               |
        +---------------+---------------+
                        v
          Backend / Frontend / Integration
                        |
                        v
            QA / Critic / Review / Security
                        |
                        v
                Coordinated PR Set
                        |
                        v
                    Human Gate
                        |
                        v
                      DONE
```

## Repository Profile

Every repository attached to a Project has its own profile:

- alias and repository kind;
- repository path/clone source;
- default base branch;
- branch prefix;
- optional per-WorkItem base branch override;
- Java/Node/runtime metadata;
- setup/build/test/start commands;
- versioned instructions path;
- future execution environment profile;
- future secrets/network policy;
- future IDE/MCP bindings.

Examples:

```text
legacy
  kind: LEGACY_BACKEND
  base: develop
  branchPrefix: feature/ai-
  java: 8
  build: ./mvnw -Plegacy package
  test: ./mvnw test
  instructions: .ai/legacy/AGENTS.md

backend
  kind: BACKEND
  base: main
  branchPrefix: ai/
  java: 25
  build: ./mvnw package
  test: ./mvnw test

frontend
  kind: FRONTEND
  base: main
  branchPrefix: ai/
  node: 24
  build: pnpm build
  test: pnpm test
```

## Definition of complete

### 1. Multi-repository & multi-branch — IN PROGRESS
- [x] Repository profiles per Project
- [x] WorkItem-to-repository bindings
- [x] Per-repository base branch and branch prefix
- [x] Per-WorkItem base branch override
- [x] Multi-root worktree workspace
- [x] Coordinated multi-repository diff
- [x] Versioned repository instructions in agent context
- [x] Coordinated Draft PR set per WorkItem
- [ ] Cross-repository merge readiness and dependency ordering
- [ ] Per-repository CI status aggregation

### 2. Execution environments
- [ ] ExecutionBackend abstraction
- [ ] LOCAL_WORKTREE backend
- [ ] DOCKER sandbox backend
- [ ] SELF_HOSTED_WORKER backend
- [ ] environment profile as code
- [ ] setup/install/start hooks
- [ ] dependency/cache snapshots
- [ ] CPU/memory/time quotas
- [ ] secret allowlists and redaction
- [ ] network deny-by-default policies

### 3. Instructions, skills and context
- [x] AGENTS.md/.ai instruction loading
- [ ] nested/path-scoped instructions
- [ ] Skills registry (SKILL.md)
- [ ] skill tool prerequisites
- [ ] skill enable/disable by repository and agent role
- [ ] architecture/codebase map cache
- [ ] context condensation for long sessions
- [ ] durable project/domain knowledge with provenance

### 4. Agent sessions and collaboration
- [x] persisted planning conversation
- [x] questions/answers/feedback
- [x] persisted tool executions
- [ ] explicit AgentSession aggregate
- [ ] pause/resume/cancel
- [ ] real-time human message injection while an agent runs
- [ ] retry from checkpoint
- [ ] branch/fork an agent session
- [ ] sub-agent delegation
- [ ] critic/verifier sub-agent

### 5. Verification and proof of work
- [x] QA Agent
- [x] Reviewer Agent
- [x] Security Reviewer
- [ ] repository-defined verification matrix
- [ ] lint/static analysis/coverage gates
- [ ] migration/schema checks
- [ ] contract tests across repositories
- [ ] browser/computer-use verification
- [ ] screenshots/videos/log artifacts
- [ ] JetBrains IDE semantic index tools
- [ ] JetBrains debugger MCP tools

### 6. Governance
- [x] deny-by-default ToolPolicy
- [x] human gates
- [ ] tool capabilities (READ/WRITE/EXECUTE/NETWORK/DB/GIT/PROD)
- [ ] risk score per WorkItem and tool call
- [ ] policy-driven approval requests
- [ ] autonomous-mode levels
- [ ] cost/token/time budgets
- [ ] model fallback/circuit breakers
- [ ] secret scanning/redaction
- [ ] immutable audit/event log

### 7. Telemetry and economics
- [ ] provider/model per LLM call
- [ ] input/output/cached tokens
- [ ] estimated and actual cost
- [ ] latency and tool time
- [ ] cost per WorkItem / Project / Wave
- [ ] first-pass review rate
- [ ] human intervention rate
- [ ] regression/rework rate
- [ ] lead time / cycle time
- [ ] model quality comparison

### 8. Integrations
- [ ] Trello adapter with card -> Project/WorkItem sync
- [ ] Planning questions as Trello comments
- [ ] human answers/approval from Trello
- [x] GitHub Draft PR publishing
- [ ] GitHub review/comment feedback ingestion
- [ ] GitHub CI/check aggregation
- [ ] webhooks/event triggers
- [ ] future Slack/Teams adapter

### 9. Control-plane UI
- [ ] Project dashboard
- [ ] DAG/waves visualization
- [ ] live agent sessions
- [ ] planning inbox (questions requiring human answer)
- [ ] approval inbox
- [ ] multi-repo diff/PR view
- [ ] tools/MCP/policies administration
- [ ] cost/quality dashboard
- [ ] artifacts and logs

## Product principles

1. **Human uncertainty is a state, not a prompt failure.** Agents stop and ask when business truth is missing.
2. **Repository boundaries are first-class.** A legacy repository never silently inherits modern-project conventions.
3. **Execution is isolated.** Parallel agents never share a mutable checkout.
4. **No implicit production access.** Production/network/database mutation requires explicit capability and policy.
5. **Evidence before approval.** Tests, diffs, CI, artifacts and reviews are part of the result.
6. **Models are replaceable.** Agent roles depend on capabilities/tasks, not provider names.
7. **Everything important is auditable.** Human decisions, model calls, tool calls, policies and PRs have provenance.
8. **Cost is an engineering metric.** A feature is not complete operationally until cost, duration and intervention are observable.
