# Execution Environments

The orchestrator can isolate command execution per repository profile. This is designed for mixed estates where legacy and modern repositories require different runtimes, branches and dependency stacks.

## Backends

- `LOCAL_WORKTREE`: executes an allow-listed command directly in the repository worktree with a reduced environment.
- `DOCKER`: executes the same allow-listed command inside an ephemeral Docker container. Docker itself is never exposed as an agent tool.

`CommandPolicy` remains the first command boundary. A model can request Maven/npm/etc. only when that executable is allow-listed. The Docker backend internally constructs `docker run`; the model cannot supply Docker flags.

## Docker sandbox defaults

A Docker execution applies:

- `--rm` and a unique container name;
- `--cap-drop ALL`;
- `no-new-privileges`;
- CPU, memory and PID limits;
- task workspace mounted at `/workspace`;
- repository-specific working directory;
- `--network none` when the profile uses `DENY`;
- only explicitly allow-listed environment variable names are injected into the container.

On timeout/interruption the orchestrator attempts `docker rm -f` for the sandbox.

## Configure a repository environment

```http
PUT /api/projects/{projectId}/repositories/{repositoryId}/environment
Content-Type: application/json
```

### Legacy Java 8 example

```json
{
  "backendType": "DOCKER",
  "containerImage": "maven:3.8.8-eclipse-temurin-8",
  "networkPolicy": "OUTBOUND",
  "cpuLimit": 2,
  "memoryLimitMb": 3072,
  "pidsLimit": 256,
  "timeoutSeconds": 1200,
  "setupCommand": "mvn -q dependency:go-offline",
  "envAllowlist": ["MAVEN_OPTS"],
  "secretAllowlist": []
}
```

The legacy repository profile can independently use `javaVersion=8`, a release/develop base branch and its own build/test commands.

### Modern Java 25 example

```json
{
  "backendType": "DOCKER",
  "containerImage": "maven:3.9.11-eclipse-temurin-25",
  "networkPolicy": "OUTBOUND",
  "cpuLimit": 4,
  "memoryLimitMb": 4096,
  "pidsLimit": 512,
  "timeoutSeconds": 900,
  "setupCommand": "./mvnw -q dependency:go-offline",
  "envAllowlist": ["MAVEN_OPTS"],
  "secretAllowlist": []
}
```

### Frontend example

```json
{
  "backendType": "DOCKER",
  "containerImage": "node:24-bookworm",
  "networkPolicy": "OUTBOUND",
  "cpuLimit": 3,
  "memoryLimitMb": 4096,
  "pidsLimit": 512,
  "timeoutSeconds": 900,
  "setupCommand": "pnpm install --frozen-lockfile",
  "envAllowlist": ["NODE_OPTIONS"],
  "secretAllowlist": ["NPM_TOKEN"]
}
```

Secrets are resolved only from the orchestrator process environment and are never stored as values in the database. Only the variable names are persisted.

## Setup hooks

When a task workspace is prepared, each bound repository with an enabled environment profile and `setupCommand` is prepared once. A marker is stored under the task workspace `.aidev-runtime/`, outside the repository worktree, so it does not appear in Git diffs.

Setup commands do not run through a shell. The parser supports quoted arguments but rejects shell metacharacters such as `|`, `&&`, `;`, redirects, backticks and `$` substitution.

## Agent command routing

`run_command` now accepts an optional `cwd`:

```json
{
  "command": ["mvn", "test"],
  "cwd": "legacy"
}
```

For multi-repository tasks `cwd` identifies the repository root and therefore its execution environment. If the WorkItem has more than one repository and `cwd` is omitted, execution is rejected as ambiguous.

For a single bound repository the repository root is selected automatically.

## Environment profile lifecycle

```http
GET  /api/projects/{projectId}/repositories/{repositoryId}/environment
PUT  /api/projects/{projectId}/repositories/{repositoryId}/environment
POST /api/projects/{projectId}/repositories/{repositoryId}/environment/enable
POST /api/projects/{projectId}/repositories/{repositoryId}/environment/disable
```

`LOCAL_WORKTREE` remains available for trusted local development. `DOCKER` is recommended for heterogeneous or fragile repositories and for higher levels of agent autonomy.
