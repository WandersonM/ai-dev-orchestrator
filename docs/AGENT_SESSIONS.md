# Durable Agent Sessions

Agent sessions are the execution-control layer for long-running tool agents. They are deliberately separate from WorkItem workflow state.

## Goals

- pause/resume without corrupting WorkItem state;
- cooperative cancellation at safe points;
- durable checkpoints and heartbeats;
- human message injection while an agent is running;
- provider-agnostic recovery using persisted local state;
- auditable lifecycle per WorkItem and AgentType.

## Safe points

The tool loop checks session control:

1. before each LLM turn;
2. after each LLM turn;
3. after every tool execution.

Pause/cancel is therefore cooperative: a running external tool is not killed mid-write. The command completes (or reaches its own timeout), then the agent acknowledges the control request.

## Lifecycle

```text
CREATED -> RUNNING
RUNNING -> PAUSE_REQUESTED -> PAUSED -> RUNNING
RUNNING -> CANCEL_REQUESTED -> CANCELLED
RUNNING -> COMPLETED
RUNNING -> FAILED
```

## API

```text
GET  /api/work-items/{workItemId}/agent-sessions
GET  /api/agent-sessions/{id}
GET  /api/agent-sessions/{id}/messages
GET  /api/agent-sessions/{id}/checkpoints
POST /api/agent-sessions/{id}/pause
POST /api/agent-sessions/{id}/resume
POST /api/agent-sessions/{id}/cancel
POST /api/agent-sessions/{id}/messages
```

Human message example:

```json
{
  "content": "Antes de alterar o contrato, confirme como o legado trata títulos vencidos.",
  "providedBy": "wanderson"
}
```

Pending HUMAN messages are consumed at the next safe point. The current provider turn is then abandoned and a fresh provider-agnostic turn is created with persisted tool history plus the latest human guidance.

## Recovery

The local database is the source of truth. Provider `turnId` values are checkpoint metadata and an optimization for an active conversation, not the only recovery mechanism.

When the orchestrator restarts, an active session can be reopened. Current step, tool history, checkpoints and human guidance remain available.

## Checkpoints

Checkpoint types include:

- SESSION_STARTED
- BEFORE_LLM
- AFTER_LLM
- AFTER_TOOL
- HUMAN_MESSAGE_APPLIED
- PAUSED
- RESUMED
- CANCELLED
- COMPLETED
- FAILED

Checkpoint summaries are intentionally bounded; full tool payload/output audit remains in `tool_execution`.

## Deliberate next step: arbitrary checkpoint replay/fork

True retry/fork from an arbitrary checkpoint is not implemented yet. Doing it correctly requires ToolExecution lineage by AgentSession (and eventually workspace/git snapshot identity) so a fork cannot accidentally replay tool state that occurred after the selected checkpoint.

The intended design is:

```text
AgentSession
  parentSessionId
  resumeCheckpointId

ToolExecution
  sessionId

Checkpoint
  workspace/git snapshot metadata
```

Only after this lineage exists should `retry-from-checkpoint` be exposed.
