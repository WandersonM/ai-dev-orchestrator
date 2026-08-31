# Architecture

The orchestrator follows a modular monolith approach with explicit boundaries for work items, workflow, agents, LLM integration, execution audit and workspace execution.

The LLM is never allowed to execute arbitrary shell commands directly. Tool execution must flow through controlled adapters and policies.
