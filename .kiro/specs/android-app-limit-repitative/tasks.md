# Implementation Plan: Repetitive App-Limit Interception

## Overview

The Screen-Time Interception Engine already ships (see the `screen-time-interception-engine` spec): the foreground service, sampling loop, permission handling, Suppression_Context deferral, and overlay window all exist. This plan targets **only the deltas** for the `Repetitive` limit type, using the component labels from the design: **[NEW]** components are created, **[MODIFY]** components are changed in place, and **[EXISTING]** components (`InterceptionConfig`, `AllowanceTracker` `Once` path, `SuppressionContextDetector`, `DeferredInterceptCoordinator`, `OverlayService`) are reused untouched except where integration requires it.

Sequencing puts domain deltas first (enums, entity fields, audit entity + port), then the data-layer schema/migration and repository mapping, then the interception-runtime `RepetitiveSessionTracker` and service routing, then the overlay actions + audit logging, then the authoring UI. Each task builds on prior tasks and ends wired into the engine or UI — no orphaned code.

Language: Kotlin (JVM 17). Module boundaries: `domain` is pure Kotlin (no Android deps); repository ports live in `domain`, implementations in `data`; `ui` is Compose-only; `interception` depends on `domain` + `data`. DI via Hilt (KSP); Room via KSP; async via Coroutines/Flow. All new user-facing strings are added to `res/values`, `res/values-hi`, and `res/values-mr` in the same task that introduces them. All Rule and audit operations are scoped to the authenticated `user_id`; intercept-action records never leave the device.

Property tests use Kotest (≥100 iterations), reuse existing `TestFakes` / injectable `java.time.Clock` + `ZoneId`, and each is tagged `// Feature: android-app-limit-repitative, Property {N}`. The 80% JaCoCo gate applies.

## Tasks

- [x] 1. Domain layer: limit-type, monitoring-state, and audit model [NEW / MODIFY]
  - [x] 1.1 Add `LimitType` and `MonitoringState` enums
    - Add `domain/entity/LimitType.kt` (`Once`, `Repetitive`) and `domain/entity/MonitoringState.kt` (`ACTIVE`, `PAUSED_FOR_OVERLAY`, `SUPPRESSED`) — pure Kotlin, no Android imports
    - _Requirements: 1.1, 2.5_

  - [x] 1.2 Extend `InterceptionRule` with limit type and interval
    - Modify `domain/entity/InterceptionRule.kt`: add `limitType: LimitType = LimitType.Once` and `repetitiveIntervalMinutes: Int? = null`; extend `init` to require `repetitiveIntervalMinutes == null` for `Once` and `in 1..120` for `Repetitive`
    - _Requirements: 1.1, 1.2, 1.5_

  - [x] 1.3 Add `InterceptActionAudit` entity and repository port
    - Add `domain/entity/InterceptActionAudit.kt` (with `InterceptActionType { CONTINUE, SWITCH_TO_DOPASHIFT }`) and `domain/repository/InterceptActionAuditRepository.kt` with `append(userId, audit): Result<Unit>` and `listForUser(userId)`
    - _Requirements: 3.4, 3.5_

  - [x] 1.4 Write unit tests for entity invariants
    - Test `InterceptionRule` rejects interval on `Once`, rejects interval outside 1..120 on `Repetitive`, accepts valid combinations
    - _Requirements: 1.5_

- [x] 2. Data layer: Room schema, migration, and audit DAO [MODIFY / NEW]
  - [x] 2.1 Add `limitType` + `repetitiveIntervalMinutes` columns and migrate
    - Modify `data/local/entity/LocalEntities.kt`: add `limitType: String` (default `ONCE`) and `repetitiveIntervalMinutes: Int?` to `LocalInterceptionRule`
    - Bump Room schema version; add a Room `Migration` running `ALTER TABLE interception_rules ADD COLUMN limitType TEXT NOT NULL DEFAULT 'ONCE'` and `ALTER TABLE interception_rules ADD COLUMN repetitiveIntervalMinutes INTEGER`; register in the database builder
    - _Requirements: 1.7_

  - [x] 2.2 Add `LocalInterceptActionAudit` entity and DAO
    - Add `LocalInterceptActionAudit` (table `intercept_action_audit`, `userId` indexed) to `LocalEntities.kt` and register in the database; add `data/local/dao/InterceptActionAuditDao.kt` with user-scoped `insert` and `listByUser`; add the table to the same schema-version migration as 2.1
    - _Requirements: 3.4, 3.5_

  - [x] 2.3 Write instrumented Room migration test
    - Verify the migration preserves existing rows, defaults `limitType` to `ONCE`, adds `repetitiveIntervalMinutes` as nullable, and creates the `intercept_action_audit` table
    - _Requirements: 1.7, 3.4_

- [x] 3. Data layer: repository mapping and audit implementation [MODIFY / NEW]
  - [x] 3.1 Map new Rule fields in `InterceptionRuleRepositoryImpl`
    - Modify `data/repository/InterceptionRuleRepositoryImpl.kt`: map `limitType` and `repetitiveIntervalMinutes` in both directions (`InterceptionRule ↔ LocalInterceptionRule`); preserve existing user-scoping and offline-first `Result` semantics (failed write preserves prior state)
    - _Requirements: 1.7, 1.8_

  - [x] 3.2 Implement `InterceptActionAuditRepositoryImpl` and Hilt binding
    - Add `data/repository/InterceptActionAuditRepositoryImpl.kt` over `InterceptActionAuditDao`, scoped by `userId`, no network dependency; bind the port in the data-module Hilt module (KSP)
    - _Requirements: 3.4, 3.5_

  - [x] 3.3 Write property test: Rule persistence round-trip with limit type
    - **Property 3: Rule persistence round-trip with limit type** (covers `Once` and `Repetitive`, plus write-failure preserves prior state — use fault-injecting fake)
    - **Validates: Requirements 1.7, 1.8**

  - [x] 3.4 Write property test: audit entry is written locally and never transmitted
    - **Property 10: Every action writes exactly one local audit entry, never transmitted** (append path only; sync-payload absence)
    - **Validates: Requirements 3.4, 3.5**

- [x] 4. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Interception runtime: `RepetitiveSessionTracker` [NEW]
  - [x] 5.1 Implement the per-cycle session tracker
    - Add `interception/RepetitiveSessionTracker.kt` (`@Singleton`): `onForeground(pkg, elapsedSeconds)` accumulates `Session_Usage` only while `ACTIVE`, returns `IntervalBreached` when usage ≥ interval; `markPausedForOverlay(pkg)` sets `PAUSED_FOR_OVERLAY` and freezes accumulation; `onOverlayDismissedAndReturned(pkg)` resets `Session_Usage` to 0 and returns to `ACTIVE`; also reset on foreground loss during deferral (OQ-2); expose `stateOf(pkg)`
    - _Requirements: 2.2, 2.3, 2.5, 2.6, 2.7_

  - [x] 5.2 Write property test: Session_Usage accumulates only while ACTIVE
    - **Property 4: Session_Usage accumulates only while ACTIVE**
    - **Validates: Requirements 2.2, 2.6**

  - [x] 5.3 Write property test: overlay present pauses accumulation
    - **Property 6: Overlay present pauses accumulation**
    - **Validates: Requirements 2.5, 2.6**

  - [x] 5.4 Write property test: dismissal resets and resumes
    - **Property 7: Dismissal resets and resumes**
    - **Validates: Requirements 2.7**

- [x] 6. Interception runtime: service routing for repetitive rules [MODIFY]
  - [x] 6.1 Route repetitive rules through the tracker, deferral, and overlay
    - Modify `interception/InterceptionService.kt`: for a `Repetitive` Rule, call `RepetitiveSessionTracker.onForeground`; on `IntervalBreached`, route through the existing `DeferredInterceptCoordinator` (Suppression_Context check + 300s deferral), present the overlay, and call `markPausedForOverlay`; keep `Once` rules on the existing `AllowanceTracker` path unchanged; preserve foreground-service, permission-per-poll, and `START_STICKY` behavior
    - _Requirements: 2.1, 2.3, 2.4, 2.5_

  - [x] 6.2 Write property test: interval breach triggers presentation within one sampling interval
    - **Property 5: Interval breach triggers presentation within one sampling interval** (subject to suppression check)
    - **Validates: Requirements 2.3, 2.4**

  - [x] 6.3 Write unit/wiring test for repetitive routing
    - Extend the interception service wiring test to assert a `Repetitive` breach routes through deferral before presenting and transitions to `PAUSED_FOR_OVERLAY`
    - _Requirements: 2.4, 2.5_

- [x] 7. Overlay: two explicit actions and audit logging [MODIFY]
  - [x] 7.1 Add the two action controls and emit an action result
    - Modify `interception/overlay/InterceptOverlayScreen.kt` and `OverlayViewModel.kt`: render "Continue to app" and "Switch to DopaShift" as the two primary controls; emit an `InterceptAction` result the service consumes; read action labels from `strings.xml`
    - Add the action-label strings to `res/values`, `res/values-hi`, `res/values-mr`
    - _Requirements: 3.1, 3.6_

  - [x] 7.2 Wire action routing and audit append in the service
    - Modify `InterceptionService.kt`: on "Continue to app" remove overlay, return focus, reset `Session_Usage`, set `ACTIVE`; on "Switch to DopaShift" remove overlay, launch DopaShift main activity, reset `Session_Usage`, keep `ACTIVE`; on either action append one `InterceptActionAudit` via `InterceptActionAuditRepository`
    - _Requirements: 3.2, 3.3, 3.4_

  - [x] 7.3 Write property test: Continue action routing
    - **Property 8: Continue action routing**
    - **Validates: Requirements 3.1, 3.3**

  - [x] 7.4 Write property test: Switch action routing
    - **Property 9: Switch action routing**
    - **Validates: Requirements 3.1, 3.2**

- [x] 8. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Rule Authoring UI: limit-type selector and interval picker [MODIFY]
  - [x] 9.1 Add limit-type/interval state and validation to the ViewModel
    - Modify `ui/interception/RuleAuthoringUiState.kt` and `RuleAuthoringViewModel.kt`: add `limitType` (default `Once`), `repetitiveIntervalMinutes`, and `intervalError`; when `Repetitive`, validate the interval as a whole number in 1..120, rejecting invalid values while retaining the last valid value and exposing the range message; persist `limitType` + interval via `InterceptionRuleRepository` on save, preserving prior state on write failure
    - _Requirements: 1.1, 1.2, 1.5, 1.6, 1.7, 1.8_

  - [x] 9.2 Render the selector and interval picker in the screen
    - Modify `ui/interception/RuleAuthoringScreen.kt`: render the mandatory Once/Repetitive selector defaulting to `Once`; show the existing 1–480 daily input for `Once` and the 1–120 interval picker for `Repetitive` with the range message
    - Add the selector labels, interval-picker label, and "Interval must be between 1 and 120 whole minutes" message to `res/values`, `res/values-hi`, `res/values-mr`
    - _Requirements: 1.1, 1.3, 1.4, 1.6, 3.6_

  - [x] 9.3 Write property test: limit-type selector defaults to Once
    - **Property 1: Limit-type selector defaults to Once**
    - **Validates: Requirements 1.1, 1.2**

  - [x] 9.4 Write property test: repetitive-interval validation (accept in range, reject-and-retain)
    - **Property 2: Repetitive-interval validation (accept in range, reject-and-retain)**
    - **Validates: Requirements 1.5, 1.6**

  - [x] 9.5 Write UI/instrumented test for authoring behaviors
    - Test default selection is `Once`, switching to `Repetitive` shows the interval picker, and an out-of-range interval retains the last valid value with the range message
    - _Requirements: 1.1, 1.4, 1.6_

- [x] 10. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Components labeled [EXISTING] in the design (`InterceptionConfig`, the `AllowanceTracker` `Once` path, `SuppressionContextDetector`, `DeferredInterceptCoordinator`, `OverlayService`) are NOT rebuilt — the repetitive path reuses them and only their integration points are touched.
- Each test task references a design Correctness Property (1–10) and the requirement ACs it validates; property tests reuse existing `TestFakes` and injectable `Clock` + `ZoneId`.
- All new user-facing strings are added to en/hi/mr in the same task that introduces them, per localization rules.
- All Rule and intercept-action-audit operations are scoped to the authenticated `user_id`; individual intercept-interaction records never leave the device.
- Per OQ-3, one active Rule per app package: the Limit_Type selector edits the single Rule rather than creating a second one.
- The 80% JaCoCo coverage gate applies to the module changes.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.3"] },
    { "id": 1, "tasks": ["1.2", "1.4"] },
    { "id": 2, "tasks": ["2.1", "2.2"] },
    { "id": 3, "tasks": ["2.3", "3.1", "3.2", "5.1"] },
    { "id": 4, "tasks": ["3.3", "3.4", "5.2", "5.3", "5.4"] },
    { "id": 5, "tasks": ["6.1"] },
    { "id": 6, "tasks": ["6.2", "6.3", "7.1"] },
    { "id": 7, "tasks": ["7.2"] },
    { "id": 8, "tasks": ["7.3", "7.4", "9.1"] },
    { "id": 9, "tasks": ["9.2"] },
    { "id": 10, "tasks": ["9.3", "9.4", "9.5"] }
  ]
}
```
