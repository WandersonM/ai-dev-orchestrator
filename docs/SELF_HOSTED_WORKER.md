# Self-hosted execution worker

The orchestrator supports three execution backends per repository environment profile:

- `LOCAL_WORKTREE`
- `DOCKER`
- `SELF_HOSTED_WORKER`

`SELF_HOSTED_WORKER` is intentionally designed around a shared or externally synchronized workspace. The orchestrator never sends an arbitrary absolute filesystem path to another machine. It sends paths relative to the configured workspace root, an allow-listed command, a bounded timeout and only environment variables already permitted by the repository EnvironmentProfile.

## When to use it

Use a worker when a repository needs a runtime that should not live on the orchestrator host, for example:

- a legacy Java 8/WildFly build machine;
- a frontend worker with browsers and Node tooling;
- a high-memory integration-test worker;
- a machine inside a private network that hosts development-only dependencies.

For fully remote/cloud execution without a shared workspace, the next evolution is an artifact/source-transfer protocol. The current worker deliberately does **not** pretend a local path exists on a remote host.

## Workspace contract

If the orchestrator uses:

```text
/opt/aidev/workspace/worktrees/CARD-123/backend
```

and its configured workspace root is:

```text
/opt/aidev/workspace
```

the worker receives only:

```text
taskPath: worktrees/CARD-123
workingDirectory: backend
```

The worker resolves these paths under its own `AIDEV_WORKER_WORKSPACE_ROOT` and rejects path traversal.

The workspace can be provided by NFS/EFS, a shared Docker volume, or another synchronization mechanism managed outside the worker protocol.

## Run a worker

Run a second instance of the same application, preferably on a private interface and behind TLS:

```bash
SERVER_PORT=8090 \
AIDEV_WORKER_SERVER_ENABLED=true \
AIDEV_WORKER_TOKEN='replace-me' \
AIDEV_WORKER_WORKSPACE_ROOT=/opt/aidev/workspace \
java -jar ai-dev-orchestrator.jar
```

The worker endpoints exist only when `server-enabled=true`:

```text
GET  /internal/worker/health
POST /internal/worker/execute
```

If a worker token is configured, both endpoints require `Authorization: Bearer <token>`.

## Configure the orchestrator client

```bash
AIDEV_WORKER_CLIENT_ENABLED=true
AIDEV_WORKER_BASE_URL=https://worker.internal.example
AIDEV_WORKER_TOKEN='replace-me'
```

Then select `SELF_HOSTED_WORKER` in the repository EnvironmentProfile.

## Security properties

The worker:

- resolves all paths under a configured workspace root;
- still passes commands through the server-side `CommandPolicy`;
- does not accept shell strings, only argument arrays;
- receives only environment variables selected by the repository EnvironmentProfile;
- caps a single request timeout at one hour;
- uses request IDs and a bounded result cache to make immediate HTTP retries idempotent;
- does not log environment values or secrets.

Use HTTPS/mTLS or a private network in real deployments. The bearer token is an application-level guard, not a substitute for transport security.
