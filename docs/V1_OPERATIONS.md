# V1 Operations & Dogfooding Runbook

This runbook is the release gate for the first operational version of AI Dev Orchestrator.

## 1. Start safely

Use PostgreSQL and start the orchestrator bound to localhost. Keep GitHub publication, Trello and remote workers disabled until the local smoke test is green.

Required secrets are supplied only through environment variables. Never commit `.env` with real values.

Minimum:

```bash
export OPENAI_API_KEY='...'
export GEMINI_API_KEY='...'
export AIDEV_WORKSPACE_ROOT="$HOME/aidev-workspace"
./mvnw spring-boot:run
```

Open `http://127.0.0.1:8080/` and `http://127.0.0.1:8080/swagger-ui.html`.

If the control plane is exposed outside localhost, configure `AIDEV_CONTROL_TOKEN` and an explicit server address/reverse proxy with TLS.

## 2. Repository onboarding gate

For every repository define and verify:

- alias and repository kind;
- source path under the configured workspace root;
- base branch and branch prefix;
- runtime metadata;
- build and test commands;
- optional `.ai/AGENTS.md` / nested `AGENTS.md` rules;
- optional `.ai/skills/*/SKILL.md` skills;
- execution environment profile;
- verification profile: lint, coverage, contract and migration commands when applicable.

Do not reuse a modern runtime profile for a legacy repository.

## 3. Controlled E2E acceptance task

Choose a small, reversible feature with a clear expected behavior. The first dogfood task must not require production credentials or destructive database operations.

Expected lifecycle:

```text
NEW
 -> PLANNING
 -> WAITING_FOR_USER_INPUT (when business truth is missing)
 -> READY_FOR_PLANNING_REVIEW
 -> READY_FOR_DOMAIN_VALIDATION
 -> READY_FOR_ARCHITECTURE
 -> READY_FOR_DEVELOPMENT
 -> INTEGRATING (when required)
 -> QA_VALIDATING
 -> REVIEWING + Critic
 -> SECURITY_REVIEWING
 -> RELEASE_PREPARING
 -> READY_FOR_HUMAN_REVIEW
 -> coordinated Draft PR(s)
 -> GitHub CI/review
 -> merged
 -> DONE
```

Acceptance evidence:

- planning questions are answerable without losing history;
- no blocking assumption is silently invented;
- every repository uses the configured branch/runtime;
- all writes occur in isolated worktrees/workspaces;
- verification matrix executes successfully;
- relevant logs/reports/screenshots are registered as artifacts;
- high-risk tool calls require the expected approval;
- audit timeline contains session/tool/human/artifact events;
- Draft PRs contain the intended diff only;
- GitHub CI readiness reports every repository independently;
- GitHub review feedback reaches the active/rework agent loop;
- all coordinated PRs must be merged before DONE.

## 4. Legacy acceptance task

Repeat with a legacy repository using a different base branch and runtime. Validate specifically:

- nested repository instructions override/generalize correctly by path scope;
- command allowlist contains only executables the legacy build actually needs;
- self-hosted worker or Docker profile is used when the host runtime is incompatible;
- modern repository conventions do not leak into the legacy prompt/context;
- verification commands match the legacy project's actual build system.

## 5. Operational metrics to capture

After each dogfood WorkItem inspect:

```text
GET /api/analytics/projects/{projectId}/delivery
GET /api/telemetry/work-items/{workItemId}/summary
GET /api/audit/work-items/{workItemId}
GET /api/artifacts/work-item/{workItemId}
GET /api/work-items/{workItemId}/github-readiness
```

Track at minimum:

- estimated LLM cost;
- total/cached tokens;
- LLM latency;
- human intervention rate;
- review iterations / first-pass completion;
- verification failures;
- cycle time to publication;
- wave failures and duration;
- provider/model distribution.

## 6. Failure handling

When a dogfood run fails:

1. do not manually patch the shared source checkout;
2. inspect AgentSession checkpoints, audit events and artifacts;
3. pause/cancel the session if it is still active;
4. fork from the last known-good restorable checkpoint when appropriate;
5. turn reproducible orchestrator defects into automated tests before continuing;
6. record confirmed business discoveries in Project Knowledge with provenance, not in ad-hoc prompts.

## 7. Production-like integration enablement

Only after local dogfooding is green:

- enable GitHub publication and configure a least-privilege token;
- configure `AIDEV_GITHUB_WEBHOOK_SECRET` and signed webhook delivery;
- enable Trello with dedicated API credentials;
- configure a bearer token for every self-hosted worker;
- expose workers only on trusted/private networks;
- use Docker `DENY` network policy by default and allow network only for repositories that require it;
- never put production secrets into broad environment allowlists.

## V1 release gate

V1 is accepted when one modern and one legacy controlled task complete the lifecycle with expected evidence, no workspace escape, no unapproved high-risk mutation, correct multi-repository CI readiness and a usable analytics/audit trail.
