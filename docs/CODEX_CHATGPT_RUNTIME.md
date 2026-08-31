# Codex CLI Runtime with ChatGPT account

The orchestrator supports delegating selected agent roles to the local OpenAI Codex CLI. This mode is intended for developers who already use Codex through their ChatGPT plan and want the orchestrator to reuse that authenticated local client instead of treating every coding turn as an OpenAI API call.

## Important billing/auth distinction

There are two different OpenAI integrations in this project:

1. `OPENAI_API_KEY` -> direct OpenAI API through `LlmGateway`.
2. Codex CLI authenticated with `codex login` -> delegated Codex runtime using the local Codex client's ChatGPT authentication.

A ChatGPT subscription is not an API key and does not convert the normal OpenAI API into subscription usage. The orchestrator therefore does not try to extract ChatGPT OAuth tokens or emulate the Codex backend. It simply launches the official local `codex` executable that is already authenticated by the user.

The orchestrator never persists, logs or copies Codex credentials.

## Install and authenticate

Install the official Codex CLI, then authenticate interactively once:

```bash
npm install -g @openai/codex
codex login
codex login status
```

`codex login status` should report that you are logged in using ChatGPT.

You can also verify through the orchestrator after startup:

```bash
curl http://localhost:8080/api/codex/status
```

Example response:

```json
{
  "installed": true,
  "enabled": true,
  "version": "codex-cli ...",
  "loggedIn": true,
  "loginStatus": "Logged in using ChatGPT",
  "delegatedRoles": [
    "BACKEND_DEVELOPER",
    "FRONTEND_DEVELOPER",
    "INTEGRATION_ENGINEER",
    "QA_ENGINEER"
  ]
}
```

## Enable the runtime

The easiest local setup is:

```bash
export SPRING_PROFILES_ACTIVE=codex
```

The `application-codex.yml` profile enables Codex and delegates the write-capable engineering roles by default.

You can configure the roles explicitly:

```bash
export AIDEV_CODEX_ENABLED=true
export AIDEV_CODEX_ROLES=BACKEND_DEVELOPER,FRONTEND_DEVELOPER,INTEGRATION_ENGINEER,QA_ENGINEER
```

All ToolLoop-based roles are compatible with the delegated runtime, so you can also experiment with read-only roles:

```bash
export AIDEV_CODEX_ROLES=ARCHITECT,BACKEND_DEVELOPER,REVIEWER,SECURITY_REVIEWER
```

Product Planning remains on the structured LLM gateway because its question/answer protocol is persisted as typed planning data.

## Security model

Codex is started non-interactively with a sandbox chosen from the orchestrator role:

- `BACKEND_DEVELOPER`, `FRONTEND_DEVELOPER`, `INTEGRATION_ENGINEER`, `QA_ENGINEER` -> `workspace-write`.
- architecture/domain/review/security/release/critic roles -> `read-only`.

The process also receives `approval_policy="never"`. If an action requires leaving the configured sandbox, Codex cannot stop a headless orchestrator waiting for a terminal prompt; the action is denied instead.

The orchestrator prompt additionally forbids:

- push/merge/release operations;
- production access;
- modifying Git internals;
- silently changing business rules;
- committing changes (the orchestrator owns commits and PR publishing).

This delegated mode is intentionally different from native orchestrator tools. A Codex session uses Codex's sandbox/command governance rather than the orchestrator's per-tool `ToolPolicy`. For high-risk repositories, keep Codex restricted to a disposable worktree and use Docker/self-hosted execution for verification.

## ChatGPT auth versus API environment variables

When delegated Codex mode is used, the child process removes `OPENAI_API_KEY` and `CODEX_API_KEY` from its environment by default. This avoids an API credential accidentally taking precedence over the stored ChatGPT login.

Configure with:

```bash
AIDEV_CODEX_STRIP_API_KEY_ENV=true
```

This does not alter the orchestrator process environment; it only sanitizes the spawned Codex process.

## Multi-repository tasks

For multi-root WorkItems, the orchestrator selects one Git worktree as Codex's primary `-C` directory and passes the remaining worktrees with `--add-dir`.

The same feature can therefore inspect or modify coordinated backend/frontend/shared-library worktrees while every repository keeps its own branch.

## Sessions and observability

A delegated Codex execution still creates a normal `AgentSession`.

Audit events include:

- `CODEX_CLI_STARTED`
- `CODEX_CLI_COMPLETED`
- `CODEX_CLI_FAILED`

The JSONL stream is inspected for useful metadata such as Codex thread ID, token counts when emitted, observed command events, exit code and duration. The billing mode is marked `CHATGPT_SUBSCRIPTION` rather than mixed into API estimated-cost accounting.

The local Codex session is ephemeral by default because the orchestrator already owns durable `AgentSession`, Git checkpoints and workspace state:

```bash
AIDEV_CODEX_EPHEMERAL=true
```

## Operational caveats

`codex exec` is an external process. Pause and human-steering semantics are therefore weaker than the native tool loop during an active Codex turn: cancellation can terminate the process, while pause/guidance is guaranteed at the next orchestrator safe point rather than inside an arbitrary in-flight Codex model/tool action.

For tighter real-time steering in the future, the next integration level is the Codex app-server protocol instead of process-level `codex exec` delegation.

## Recommended initial setup

For the first dogfood test:

```bash
SPRING_PROFILES_ACTIVE=codex
AIDEV_CODEX_ROLES=BACKEND_DEVELOPER,QA_ENGINEER
```

Keep planning/architecture/review on Gemini/OpenAI API while Codex performs the expensive implementation/verification work. Once the workflow is stable, expand roles selectively.
