---
inclusion: always
---

# Security Framework

## Non-negotiable rules

- Auth: OAuth2/OIDC via Keycloak only. No custom auth mechanism, ever.
- Transit: TLS 1.2+ on every connection — client-backend and backend-to-backend (Postgres, Redis, Keycloak).
- At rest: BYO-LLM API keys, auth tokens, and any credential data are encrypted via the project's secrets-management mechanism (Vault/sealed secrets). Never store a secret in plaintext config, environment files committed to source control, or a database column without encryption.
- Logging: LLM API keys, auth tokens, and PII are never logged in plaintext, at any log level. Implement redaction at the logging-framework level (a filter/formatter), not as a per-call developer responsibility — a forgotten redaction call is a leak.
- Input validation: every user-supplied input (goal names, task text, keywords) is validated and sanitized server-side regardless of client-side validation. Parameterized queries/ORM only — no raw SQL string concatenation. Output-encode on the web portal to prevent XSS.
- Rate limiting on all public endpoints, stricter limits on auth endpoints specifically.
- RBAC at the API layer from v1, even with a single user role — do not hardcode single-role assumptions that block adding roles later.
- Every query is scoped by authenticated `user_id`. No endpoint returns or accepts another user's data based on a client-supplied ID alone — always cross-check against the authenticated identity.
- BYO-LLM calls (Requirement 15) send only minimum necessary context (goal name/keywords) — never raw local telemetry, never full task history.
- Users can rotate/remove their BYO-LLM API key at any time; the old key is invalidated from further use immediately, not on next login.

## CI gates

- Dependency vulnerability scan (OWASP Dependency-Check or equivalent) fails the build on critical/high findings.
- SAST (Semgrep, SonarQube security ruleset, or equivalent) runs against both backend and web portal code.
- OWASP Top 10 is the baseline checklist for every new endpoint or user-input surface — check against it explicitly when implementing, don't assume general good practice covers it.

## When generating code that touches auth, secrets, or user data

Stop and check this file before writing the code. If a shortcut would violate a rule above, flag it rather than implement it silently.
