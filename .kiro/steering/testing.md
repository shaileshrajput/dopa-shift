---
inclusion: always
---

# Testing Framework & Strategy

## Non-negotiable rules

- No implementation task is complete without a corresponding passing test. One automated test per Acceptance Criteria line in the requirements documents, minimum.
- Backend: JUnit5/Kotest for domain-logic unit tests (no Spring context loaded — fast). Testcontainers for integration tests against real Postgres/Redis, not mocks.
- Android: Espresso/Compose Test for instrumented UI tests, including overlay rendering, checkbox/strikethrough interactions, and permission-request flows (`UiAutomator` for system dialogs).
- Web: Jest for component/unit tests, Playwright for E2E including simulated offline/network-throttled scenarios.
- Sync/conflict-resolution engine gets its own dedicated, deterministic test suite: simulate concurrent offline edits (same field, different fields), reconnect in varying orders, assert the merge outcome every time. Treat this suite as higher priority than general CRUD coverage — an undetected regression here is silent data loss.
- Recurring/conditional reminder logic is tested with an injectable/fake clock — never real wall-clock delays in a test.
- BYO-LLM integration is tested against mocked provider responses: valid response, invalid/expired key, rate-limit response, and a response with no parseable video link (validates the YouTube-search fallback path fires correctly).
- Interception engine (overlay/usage-stats) is tested across representative Android API-level configurations, not a single device profile — Android Go edition included, since it's a hard capability difference, not a variation.
- OpenAPI contract tests fail the build on any response divergence from the published spec.

## CI behavior

- Unit + integration tests run on every commit/PR.
- E2E (Playwright, full Android instrumented suites) run at minimum before every release build.
- Any failing test suite fails the build — no requirement/task is marked complete on a red run.
- Domain/business-logic layer has a minimum coverage threshold (set by the team, not fabricated). Coverage percentage alone is never sufficient for the sync-engine suite — it also requires scenario-based assertions regardless of raw coverage number.

## When generating a new feature

Generate the test alongside the implementation, not after. If the feature maps to an Acceptance Criteria line already in requirements.md, write the test to assert that exact criteria.
