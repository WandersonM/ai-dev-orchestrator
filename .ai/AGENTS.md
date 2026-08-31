# Agent Operating Rules

- Never operate outside the configured workspace root.
- Never access production credentials or production databases.
- Never change business rules that are not present in the specification or repository documentation.
- Prefer modifying existing abstractions over creating duplicates.
- Every code-producing agent must run relevant tests before declaring completion.
- Every change must be reviewable through git diff.
