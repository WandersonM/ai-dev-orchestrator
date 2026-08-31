#!/usr/bin/env python3
"""Interactive local dogfood harness for AI Dev Orchestrator.

The harness creates a disposable dependency-free Node repository, registers it as a
Project/WorkItem, drives planning through human questions/approval, executes ready
waves, and verifies the resulting worktree when the workflow reaches the human gate.

No external Python packages are required.
"""

from __future__ import annotations

import argparse
import json
import os
import pathlib
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from typing import Any


HUMAN_REQUIRED = {
    "PLANNING_HUMAN_REQUIRED",
    "DOMAIN_HUMAN_REQUIRED",
    "ARCHITECTURE_HUMAN_REQUIRED",
    "RELEASE_HUMAN_REQUIRED",
}
TERMINAL = {"READY_FOR_HUMAN_REVIEW", "DONE", "FAILED"}


class Api:
    def __init__(self, base_url: str, token: str | None = None):
        self.base_url = base_url.rstrip("/")
        self.token = token

    def call(self, method: str, path: str, body: Any | None = None) -> Any:
        data = None if body is None else json.dumps(body).encode("utf-8")
        headers = {"Accept": "application/json"}
        if body is not None:
            headers["Content-Type"] = "application/json"
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        req = urllib.request.Request(self.base_url + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=1800) as response:
                raw = response.read()
                if not raw:
                    return None
                return json.loads(raw.decode("utf-8"))
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {exc.code} {method} {path}: {detail}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"Cannot reach orchestrator at {self.base_url}: {exc}") from exc


class DogfoodHarness:
    def __init__(self, api: Api, fixture: pathlib.Path, auto_approve: bool):
        self.api = api
        self.fixture = fixture.resolve()
        self.auto_approve = auto_approve
        self.project_id: str | None = None
        self.work_item_id: str | None = None

    def run(self) -> int:
        self.check_runtime()
        self.create_fixture()
        self.register_scenario()
        self.drive_workflow()
        return self.verify_result()

    def check_runtime(self) -> None:
        print("\n== Runtime preflight ==")
        status = self.api.call("GET", "/api/codex/status")
        print(json.dumps(status, indent=2, ensure_ascii=False))
        if status.get("enabled") and not status.get("loggedIn"):
            raise RuntimeError("Codex runtime is enabled but CLI is not logged in. Run: codex login")
        if status.get("enabled") and not status.get("installed"):
            raise RuntimeError("Codex runtime is enabled but the codex executable was not found")

    def create_fixture(self) -> None:
        print(f"\n== Creating disposable fixture at {self.fixture} ==")
        if self.fixture.exists():
            shutil.rmtree(self.fixture)
        (self.fixture / "src").mkdir(parents=True)
        (self.fixture / "package.json").write_text(
            json.dumps(
                {
                    "name": "aidev-dogfood-fixture",
                    "private": True,
                    "version": "1.0.0",
                    "scripts": {"test": "node test.js"},
                },
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        (self.fixture / "src" / "greeting.js").write_text(
            "function greet() {\n  return 'Hello!';\n}\n\nmodule.exports = { greet };\n",
            encoding="utf-8",
        )
        (self.fixture / "test.js").write_text(
            "const assert = require('node:assert/strict');\n"
            "const { greet } = require('./src/greeting');\n\n"
            "assert.equal(greet(), 'Hello!');\n"
            "console.log('baseline tests passed');\n",
            encoding="utf-8",
        )
        (self.fixture / "AGENTS.md").write_text(
            "# Dogfood fixture instructions\n\n"
            "- This is a deliberately tiny CommonJS Node repository.\n"
            "- Do not add dependencies.\n"
            "- Keep implementation and tests simple.\n"
            "- Run `npm test` before finishing.\n",
            encoding="utf-8",
        )
        self.git("init", "-b", "main")
        self.git("config", "user.email", "dogfood@local.invalid")
        self.git("config", "user.name", "AI Dev Dogfood")
        self.git("add", ".")
        self.git("commit", "-m", "test: initial dogfood fixture")
        self.run_command(["npm", "test"], cwd=self.fixture)

    def register_scenario(self) -> None:
        print("\n== Registering Project and WorkItem ==")
        project = self.api.call(
            "POST",
            "/api/projects",
            {
                "name": f"Dogfood {int(time.time())}",
                "description": "Disposable E2E scenario created by scripts/dogfood_e2e.py",
                "repositoryPath": str(self.fixture),
            },
        )
        self.project_id = project["id"]
        self.api.call(
            "POST",
            f"/api/projects/{self.project_id}/repositories",
            {
                "alias": "backend",
                "kind": "BACKEND",
                "repositoryPath": str(self.fixture),
                "baseBranch": "main",
                "branchPrefix": "dogfood/",
                "instructionsPath": "AGENTS.md",
                "buildCommand": "npm test",
                "testCommand": "npm test",
                "nodeVersion": "20+",
            },
        )
        item = self.api.call(
            "POST",
            f"/api/projects/{self.project_id}/work-items",
            {
                "externalId": f"DOGFOOD-{int(time.time())}",
                "title": "Add personalized greeting with blank-name validation",
                "description": (
                    "Change src/greeting.js so greet(name) returns exactly `Hello, <name>!`. "
                    "A null, undefined, empty, or whitespace-only name must throw IllegalArgumentError-equivalent "
                    "for JavaScript, specifically an Error whose type is TypeError or Error and whose message explains "
                    "that name is required. Trim leading/trailing whitespace before formatting. Update test.js to cover "
                    "a normal name, trimming, and blank rejection. Do not add dependencies. `npm test` must pass."
                ),
                "blockedBy": [],
                "repositories": [
                    {"alias": "backend", "purpose": "PRIMARY", "baseBranchOverride": "main"}
                ],
            },
        )
        self.work_item_id = item["id"]
        print(f"Project: {self.project_id}")
        print(f"WorkItem: {self.work_item_id}")

    def drive_workflow(self) -> None:
        assert self.project_id and self.work_item_id
        print("\n== Driving workflow ==")
        safety = 0
        while safety < 40:
            safety += 1
            item = self.current_item()
            status = item["status"]
            print(f"[{safety:02d}] status={status}")

            if status == "NEW":
                self.api.call("POST", f"/api/work-items/{self.work_item_id}/planning/start")
                continue

            if status == "WAITING_FOR_USER_INPUT":
                self.answer_planning_questions()
                self.api.call("POST", f"/api/work-items/{self.work_item_id}/planning/continue")
                continue

            if status == "READY_FOR_PLANNING_REVIEW":
                planning = self.api.call("GET", f"/api/work-items/{self.work_item_id}/planning")
                print("\n--- Planning result ---")
                print(json.dumps(planning, indent=2, ensure_ascii=False))
                if self.auto_approve or ask_yes_no("Approve planning specification?", default=True):
                    self.api.call("POST", f"/api/work-items/{self.work_item_id}/planning/approve")
                    continue
                feedback = input("Planning feedback: ").strip()
                if not feedback:
                    raise RuntimeError("Planning was rejected without feedback")
                self.api.call(
                    "POST",
                    f"/api/work-items/{self.work_item_id}/planning/request-changes",
                    {"feedback": feedback, "providedBy": "dogfood-human"},
                )
                continue

            if status in HUMAN_REQUIRED:
                raise RuntimeError(
                    f"Workflow reached {status}. This is a valid safety stop, but the generic dogfood harness "
                    "does not auto-resolve specialist human gates. Inspect the WorkItem in the control plane."
                )

            if status in TERMINAL:
                return

            wave = self.api.call("POST", f"/api/projects/{self.project_id}/execute-ready")
            print("wave:", json.dumps(wave, ensure_ascii=False))

        raise RuntimeError("Workflow did not reach a terminal/human gate within 40 transitions")

    def answer_planning_questions(self) -> None:
        assert self.work_item_id
        questions = self.api.call("GET", f"/api/work-items/{self.work_item_id}/planning/questions") or []
        pending = [q for q in questions if not q.get("answer")]
        if not pending:
            raise RuntimeError("Planning is waiting for user input but no unanswered questions were returned")
        print("\nPlanning Agent needs business clarification:")
        for question in pending:
            print("\n" + "-" * 72)
            print(question.get("question", question))
            rationale = question.get("rationale")
            if rationale:
                print("Why:", rationale)
            options = question.get("options") or []
            if options:
                print("Options:", ", ".join(map(str, options)))
            answer = input("Your answer: ").strip()
            if not answer:
                raise RuntimeError("Blank answers are not accepted by the E2E harness")
            self.api.call(
                "POST",
                f"/api/work-items/{self.work_item_id}/planning/questions/{question['id']}/answer",
                {"answer": answer, "answeredBy": "dogfood-human"},
            )

    def verify_result(self) -> int:
        assert self.work_item_id and self.project_id
        item = self.current_item()
        print("\n== Final WorkItem ==")
        print(json.dumps(item, indent=2, ensure_ascii=False))
        if item["status"] == "FAILED":
            return 2
        if item["status"] not in {"READY_FOR_HUMAN_REVIEW", "DONE"}:
            return 3

        print("\n== Verification matrix ==")
        verification = self.api.call("POST", f"/api/work-items/{self.work_item_id}/verify")
        print(json.dumps(verification, indent=2, ensure_ascii=False))
        run_status = (verification.get("run") or {}).get("status")
        if run_status != "PASSED":
            print(f"Verification status was {run_status}", file=sys.stderr)
            return 4

        workspace = item.get("activeWorkspacePath")
        if workspace:
            root = pathlib.Path(workspace)
            repo = root if (root / ".git").exists() else root / "backend"
            if repo.exists():
                print("\n== Git diff ==")
                self.run_command(["git", "status", "--short"], cwd=repo, check=False)
                self.run_command(["git", "diff", "--stat"], cwd=repo, check=False)
                self.run_command(["npm", "test"], cwd=repo)

        print("\n== Audit evidence ==")
        audit = self.api.call("GET", f"/api/audit/work-items/{self.work_item_id}")
        events = [event.get("eventType") for event in audit]
        print(" -> ".join(events[-20:]))
        if not any(name and name.startswith("AGENT_SESSION_") for name in events):
            print("Warning: no AgentSession audit events were found", file=sys.stderr)

        print("\n== Delivery analytics ==")
        analytics = self.api.call("GET", f"/api/analytics/projects/{self.project_id}/delivery")
        print(json.dumps(analytics, indent=2, ensure_ascii=False))
        print("\nDOGFOOD E2E PASSED: implementation reached the human gate with verification evidence.")
        return 0

    def current_item(self) -> dict[str, Any]:
        assert self.project_id and self.work_item_id
        items = self.api.call("GET", f"/api/projects/{self.project_id}/work-items")
        for item in items:
            if item["id"] == self.work_item_id:
                return item
        raise RuntimeError("Dogfood WorkItem disappeared from Project")

    def git(self, *args: str) -> None:
        self.run_command(["git", *args], cwd=self.fixture)

    @staticmethod
    def run_command(command: list[str], cwd: pathlib.Path, check: bool = True) -> subprocess.CompletedProcess[str]:
        print("$", " ".join(command))
        result = subprocess.run(command, cwd=cwd, text=True, capture_output=True)
        if result.stdout:
            print(result.stdout, end="")
        if result.stderr:
            print(result.stderr, end="", file=sys.stderr)
        if check and result.returncode != 0:
            raise RuntimeError(f"Command failed ({result.returncode}): {' '.join(command)}")
        return result


def ask_yes_no(message: str, default: bool) -> bool:
    suffix = " [Y/n] " if default else " [y/N] "
    value = input(message + suffix).strip().lower()
    if not value:
        return default
    return value in {"y", "yes", "s", "sim"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run a disposable end-to-end dogfood scenario")
    parser.add_argument("--base-url", default=os.getenv("AIDEV_BASE_URL", "http://127.0.0.1:8080"))
    parser.add_argument("--token", default=os.getenv("AIDEV_CONTROL_TOKEN"))
    parser.add_argument(
        "--fixture",
        default=os.getenv("AIDEV_DOGFOOD_FIXTURE", "./workspace/dogfood/fixture-node"),
        help="Disposable Git repository path; it is deleted/recreated by the harness",
    )
    parser.add_argument("--auto-approve-planning", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    fixture = pathlib.Path(args.fixture)
    try:
        return DogfoodHarness(Api(args.base_url, args.token), fixture, args.auto_approve_planning).run()
    except KeyboardInterrupt:
        print("\nCancelled by user", file=sys.stderr)
        return 130
    except Exception as exc:
        print(f"\nDOGFOOD E2E FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
