---
inclusion: auto
---

# API Standards

One versioned REST API (`/v1/...`), OpenAPI 3 spec as the single source of truth, consumed identically by Android, web, and any future client.

## Non-negotiable rules

- No platform-conditional fields, params, or endpoints. If Android needs something web doesn't, solve it client-side — never branch the API by platform.
- Every endpoint is defined in the OpenAPI spec before implementation. Generate code from the spec, not the other way around.
- Auth is OAuth2/OIDC bearer tokens issued by Keycloak on every request. No endpoint skips auth except explicitly documented public health/status checks.
- Breaking changes get a new version path (`/v2/...`); never mutate an existing version's contract in place.
- Every data-access query is scoped by authenticated `user_id` at the query layer — never rely on client-side filtering to enforce data ownership.
- Contract tests validate every response against the OpenAPI spec in CI. A response that diverges from the spec is a build failure, not a warning.

## When generating a new endpoint

1. Add/update the OpenAPI definition first.
2. Confirm the endpoint doesn't duplicate functionality achievable via an existing generic endpoint plus client-side logic.
3. Add the corresponding contract test in the same change.
