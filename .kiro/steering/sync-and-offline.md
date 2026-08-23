---
inclusion: always
---

# Sync Engine & Offline-First Rules

DopaShift is offline-first on every client (Android, web). Local storage is always the source of truth on that device; sync is asynchronous and never blocks a local write.

## Non-negotiable rules

- Every local write (create/edit/complete/delete) commits to local storage (Room on Android, Dexie/IndexedDB on web) immediately, regardless of connectivity. Never gate a local write on a network call succeeding.
- Sync is driven by an append-only change-log: `entity_id, field, value, timestamp, device_id, user_id`. Do not design any sync path around whole-record snapshots.
- Conflict resolution is **field-level merge with server-assigned logical timestamps** — never device wall-clock time, never whole-record Last-Write-Wins. This applies identically to `DailyTodoItem`, goal-scoped checklist items, and any other syncable entity. Do not special-case one entity type with different resolution logic.
- When the same field is edited concurrently on two devices, the losing edit is flagged as superseded and retrievable in conflict history — never silently discarded.
- On reconnect, a client pulls all change-log events since its last known sync point and applies them locally before pushing new local writes.
- The sync mechanism is shared infrastructure across entity types (to-do items, goal checklist items, habit checkpoints, reminders). Do not implement a parallel/bespoke sync path per feature — extend the shared engine.

## When generating code for a new syncable entity

1. Confirm it fits the existing change-log schema before adding fields to it.
2. Write the field-merge test scenario (two offline edits, reconnect, assert merge) before or alongside the feature code — this is the highest-risk area in the app; treat it accordingly.
3. Never assume network availability in any user-facing flow — every screen must render and be interactive from local data alone.
