# Implementation Plan: Screen-Time Interception Engine

## Overview

A substantial implementation already exists in the Android `interception` and `data` modules. This plan targets **only the deltas** identified in the design document: components labeled **[NEW]** are created, components labeled **[MODIFY]** are changed in place, and existing components are left untouched except where integration requires it.

Sequencing puts foundation deltas first (domain entity + port, Room schema/DAO), then the data-layer implementations, then the interception-engine runtime components, then the notification/overlay integration, then the net-new Rule Authoring UI. Each task builds on prior tasks and ends wired into the engine or UI — no orphaned code.

Language: Kotlin (JVM 17). Module boundaries: `domain` is pure Kotlin (no Android deps); repository ports live in `domain`, implementations in `data`; `ui` is Compose-only; `interception` depends on `domain` + `data`. DI via Hilt (KSP); Room via KSP; async via Coroutines/Flow. All new user-facing strings are added to `res/values`, `res/values-hi`, and `res/values-mr` in the same task that introduces them. All Rule operations are scoped to the authenticated `user_id`; raw telemetry never leaves the device.

Property tests use Kotest (≥100 iterations), reuse existing `TestFakes` / injectable `java.time.Clock` + `ZoneId` / `InterceptionContext` fake, and each is tagged `// Feature: screen-time-interception-engine, Property {N}`. The 80% JaCoCo gate applies.

## Tasks

- [x] 1. Domain layer: Rule entity and repository port [NEW]
  - [x] 1.1 Create `InterceptionRule` domain entity
    - Add `domain/entity/InterceptionRule.kt` (pure Kotlin, no Android imports): fields `id`, `userId`, `appPackageName`, `dailyLimitMinutes`, `enabled`, `pausedForDate: LocalDate?`, `createdAt`; `init` require `dailyLimitMinutes in 1..480`; `isPausedOn(today: LocalDate): Boolean`
    - Add `domain/entity/FocusExtensionState.kt` data class (`packageName`, `date`, `extensionsGranted`, `activeUntil: Instant?`)
    - _Requirements: 3.1, 2.6_

  - [x] 1.2 Create `InterceptionRuleRepository` port
    - Add `domain/repository/InterceptionRuleRepository.kt` with `save`, `findActiveByPackage`, `listForUser`, `observeForUser` (Flow), `updateDailyLimit`, `pauseForToday`, `delete`; every method takes authenticated `userId`; CRUD returns `Result<Unit>`
    - _Requirements: 3.1, 3.3, 3.5, 3.7, 3.8, 3.9_

  - [x] 1.3 Write unit tests for `InterceptionRule` invariants
    - Test `dailyLimitMinutes` bounds (reject <1 and >480), `isPausedOn` true/false cases
    - _Requirements: 2.6, 3.5_

- [x] 2. Data layer: Room schema, DAO, and migration [MODIFY]
  - [x] 2.1 Add `pausedForDate` to `LocalInterceptionRule` and migrate
    - Modify `data/local/entity/LocalEntities.kt`: add `pausedForDate: String?` (ISO `LocalDate`) to `LocalInterceptionRule`
    - Bump Room schema version and add a Room `Migration` running `ALTER TABLE interception_rules ADD COLUMN pausedForDate TEXT`; register the migration in the database builder
    - _Requirements: 3.5_

  - [x] 2.2 Extend `InterceptionRuleDao` with pause/list-with-paused queries
    - Modify `data/local/dao/InterceptionRuleDao.kt`: add query to set `pausedForDate` for a rule id scoped by `userId`; add list/observe queries returning rules including paused state; ensure `findActiveByPackageName` remains user-scoped
    - _Requirements: 3.2, 3.5, 3.8_

  - [x] 2.3 Write instrumented Room migration test
    - Verify migration preserves existing rows and adds `pausedForDate` as nullable
    - _Requirements: 3.5_

- [x] 3. Data layer: `InterceptionRuleRepositoryImpl` [NEW]
  - [x] 3.1 Implement the repository over the DAO with user_id scoping
    - Add `data/repository/InterceptionRuleRepositoryImpl.kt` implementing `InterceptionRuleRepository`; map `InterceptionRule ↔ LocalInterceptionRule` (`dailyLimitMinutes↔dailyAllowanceMinutes`, `enabled↔isActive`, `pausedForDate`)
    - Every read/write filters by `userId`; per-id write/delete/pause cross-checks stored `userId` against caller and rejects mismatches; writes go to Room first (offline-first); failures return `Result.failure` without mutating state
    - _Requirements: 3.1, 3.3, 3.7, 3.8, 3.9_

  - [x] 3.2 Add Hilt binding for the repository
    - Bind `InterceptionRuleRepository` to `InterceptionRuleRepositoryImpl` in the data module Hilt module (KSP)
    - _Requirements: 3.1_

  - [x] 3.3 Write property test: Rule persistence round-trip
    - **Property 8: Rule persistence round-trip**
    - **Validates: Requirements 3.1, 3.2, 3.3**

  - [x] 3.4 Write property test: user scoping isolation
    - **Property 11: User scoping isolation**
    - **Validates: Requirements 3.8**

  - [x] 3.5 Write property test: local-store write failures preserve prior state
    - **Property 12: Local-store write failures preserve prior state** (use fault-injecting fake repository)
    - **Validates: Requirements 3.9, 6.1**

- [x] 4. Interception config: reject-and-retain interval + extension duration [MODIFY]
  - [x] 4.1 Add `focusExtensionMinutes` and `setSamplingIntervalSeconds`
    - Modify `interception/InterceptionConfig.kt`: add `focusExtensionMinutes: Int = 5` constrained 1..15; add `setSamplingIntervalSeconds()` that **rejects** out-of-range (outside 3–30s) values and returns/retains the previous valid interval (no silent `coerceIn` clamp)
    - _Requirements: 1.2, 1.3, 4.10_

  - [x] 4.2 Write property test: sampling interval integrity
    - **Property 2: Sampling interval integrity (reject and retain)**
    - **Validates: Requirements 1.2, 1.3**

  - [x] 4.3 Write property test: Focus_Extension duration bounds
    - **Property 16: Focus_Extension duration bounds**
    - **Validates: Requirements 4.10**

- [x] 5. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. AllowanceTracker: port lookup, pause skip, batched persistence + retry [MODIFY]
  - [x] 6.1 Route rule lookup through the domain port and skip paused rules
    - Modify `interception/AllowanceTracker.kt`: look up the Rule via `InterceptionRuleRepository` (not the DAO directly); skip accumulation and depletion detection for a Rule that is paused-for-today; a deleted rule reports `NOT_TRACKED` within one interval
    - _Requirements: 3.5, 3.7_

  - [x] 6.2 Batch accumulation with ≥60s flush cadence and failure retry
    - Modify `AllowanceTracker`: accumulate in memory and flush to `LocalTelemetryRepository` at least once every 60s (instead of every poll); on flush failure retain the in-memory value and retry on the next cycle (no data loss, no double-count)
    - _Requirements: 1.4, 1.5, 1.6_

  - [x] 6.3 Write property test: accumulation is the monotonic sum of increments
    - **Property 1: Accumulation equals the sum of increments (monotonic)**
    - **Validates: Requirements 1.4**

  - [x] 6.4 Write property test: persistence cadence and failure retention
    - **Property 3: Persistence cadence and failure retention** (injectable Clock)
    - **Validates: Requirements 1.5, 1.6**

  - [x] 6.5 Write property test: pause-for-today suppresses then auto-resumes
    - **Property 9: Pause-for-today suppresses then auto-resumes** (injectable Clock + ZoneId)
    - **Validates: Requirements 3.5**

  - [x] 6.6 Write property test: deleting a Rule stops accumulation
    - **Property 10: Deleting a Rule stops accumulation**
    - **Validates: Requirements 3.7**

- [x] 7. FocusExtensionManager [NEW]
  - [x] 7.1 Implement the extension state machine
    - Add `interception/FocusExtensionManager.kt` (`@Singleton`, injectable `Clock` + `ZoneId`): `grantIfEngaged(pkg, engaged, config)` grants only when ≥1 action recorded, expiry = depletion time + configured duration; `isExtensionActive(pkg, now)` suppresses re-trigger while active; cap 3/day per app; return `GrantResult.Granted | NoActionRecorded | CapReached`; reset per-app counters at local midnight
    - _Requirements: 4.9, 4.10, 4.11, 4.12, 4.13_

  - [x] 7.2 Write property test: extension granted only on recorded engagement
    - **Property 15: Focus_Extension granted only on recorded engagement**
    - **Validates: Requirements 4.9**

  - [x] 7.3 Write property test: active extension suppresses re-triggering
    - **Property 17: Active extension suppresses re-triggering** (half-open interval [t0, t0+d))
    - **Validates: Requirements 4.11**

  - [x] 7.4 Write property test: extension cap never exceeds three per day with cap signalling
    - **Property 18: Extension cap never exceeds three per day, with cap signalling**
    - **Validates: Requirements 4.12, 4.13**

- [x] 8. SuppressionContextDetector [NEW]
  - [x] 8.1 Implement suppression-context detection
    - Add `interception/SuppressionContextDetector.kt`: detect active call (`TelephonyManager` call state / `AudioManager.MODE_IN_CALL`), active navigation (system signal), ringing alarm/timer, emergency dialer; expose `isSuppressed(now): SuppressionResult` naming the active context or `None`; third-party video-call apps out of scope per OQ-3 (documented in KDoc)
    - _Requirements: 5.2_

  - [x] 8.2 Write property test: never present during a Suppression_Context
    - **Property 19: Never present the Intercept_Screen during a Suppression_Context**
    - **Validates: Requirements 5.2**

- [x] 9. DeferredInterceptCoordinator [NEW]
  - [x] 9.1 Implement the 300s deferral state machine
    - Add `interception/DeferredInterceptCoordinator.kt` (injectable `Clock`): on breach while suppressed, defer up to 300s; re-check each monitoring cycle; present within 2s once suppression ends AND Monitored_App still foreground; discard if app left foreground; discard + re-evaluate on next cycle if 300s window elapses while still suppressed; consult `FocusExtensionManager.isExtensionActive` for active-extension suppression
    - _Requirements: 4.11, 5.3, 5.4, 5.5, 5.6_

  - [x] 9.2 Write property test: deferral holds up to the window bound
    - **Property 20: Deferral holds up to the window bound**
    - **Validates: Requirements 5.3**

  - [x] 9.3 Write property test: deferred intercept presents only when suppression clears in time and app is foreground
    - **Property 21: Deferred intercept presents only when suppression clears in time and app is foreground**
    - **Validates: Requirements 5.4, 5.5, 5.6**

- [x] 10. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. LimitReachedNotifier and InterceptionService wiring [NEW + MODIFY]
  - [x] 11.1 Implement `LimitReachedNotifier` with a single open-Intercept action
    - Add `interception/reminder/LimitReachedNotifier.kt`: post the limit-reached notification (within one sampling interval of breach) with a single `PendingIntent` action that opens the Intercept_Screen for the associated app; all title/text/action-label strings from `strings.xml`
    - Add the notification title, text, and action-label strings to `res/values`, `res/values-hi`, `res/values-mr`
    - _Requirements: 4.4, 4.5_

  - [x] 11.2 Route depletion through deferral and extension before overlay
    - Modify `interception/InterceptionService.kt`: on depletion, consult `FocusExtensionManager` (grace/cap) and `DeferredInterceptCoordinator` (suppression check) before showing the overlay; fire `LimitReachedNotifier`; preserve existing foreground-service, permission-per-poll, and `START_STICKY` behavior
    - _Requirements: 4.1, 4.4, 4.5, 4.11, 4.13, 5.2, 5.3, 5.4, 5.5, 5.6_

  - [x] 11.3 Write unit/wiring tests for notifier and service routing
    - Assert notification content and single action (unit); extend `InterceptionServiceWiringTest` to assert depletion routes through deferral/extension before overlay
    - _Requirements: 4.4, 4.5, 4.11_

- [x] 12. Overlay: return engagement result [MODIFY]
  - [x] 12.1 Surface an engagement result from the overlay
    - Modify `interception/overlay/*` (`OverlayViewModel` / `OverlayTodoCompletionHandler`): expose whether the user checked a habit or completed a to-do so `FocusExtensionManager.grantIfEngaged` can decide grants; preserve the existing change-log pipeline for completions
    - Wire the overlay engagement result back to `InterceptionService` → `FocusExtensionManager`
    - _Requirements: 4.7, 4.8, 4.9_

  - [x] 12.2 Write property test: Intercept_Screen content completeness
    - **Property 13: Intercept_Screen content completeness**
    - **Validates: Requirements 4.6**

  - [x] 12.3 Write property test: overlay action uses the same pipeline as normal completion
    - **Property 14: Overlay action uses the same pipeline as normal completion**
    - **Validates: Requirements 4.8**

- [x] 13. Localize existing hardcoded notifier strings [MODIFY]
  - [x] 13.1 Migrate `InterceptionTodoReminderNotifier` and `PermissionRevocationDetector` strings
    - Modify both classes to read title/summary/notification text from `strings.xml` instead of hardcoded English (e.g., "You have N pending tasks", "Time's up on your app", permission-revoked text)
    - Add all migrated strings to `res/values`, `res/values-hi`, `res/values-mr`; missing translations fall back to English
    - _Requirements: 1.7, 4.14_

- [x] 14. AggregatedCounterSync [NEW]
  - [x] 14.1 Implement consent-gated aggregated-only sync
    - Add `data/repository/AggregatedCounterSync.kt`: read a consent flag from DataStore; sync only aggregated counts / diagnostic summaries (structurally never referencing raw `LocalTelemetryEvent` rows); reuse `InterceptionRuleDto` for rule metadata; on consent revocation cancel in-flight/queued work immediately and no-op subsequent attempts until re-granted
    - _Requirements: 6.2, 6.3, 6.4, 6.5_

  - [x] 14.2 Write property test: aggregated sync payload contains no raw telemetry
    - **Property 22: Aggregated sync payload contains no raw telemetry**
    - **Validates: Requirements 6.3, 6.4**

  - [x] 14.3 Write property test: consent revocation cancels sync and halts further syncing
    - **Property 23: Consent revocation cancels sync and halts further syncing**
    - **Validates: Requirements 6.5**

- [x] 15. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 16. Rule Authoring UI: installed-app list and search [NEW]
  - [x] 16.1 Implement `InstalledAppsProvider`
    - Add `ui/interception/InstalledAppsProvider.kt`: enumerate installed launchable apps (icon + label), sorted case-insensitive alphabetically by label; exclude system-critical apps (this app, launcher, dialer, settings)
    - _Requirements: 2.1, 2.8_

  - [x] 16.2 Implement `RuleAuthoringUiState` and `RuleAuthoringViewModel` list/search/selection
    - Add `ui/interception/RuleAuthoringUiState.kt` and `RuleAuthoringViewModel.kt` (Hilt): expose sorted app list, case-insensitive search filter, empty-result state that retains selections, selection of 0..N apps
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.8_

  - [x] 16.3 Write property test: installed-app list is sorted and complete, excluding critical apps
    - **Property 4: Installed-app list is sorted and complete, excluding critical apps**
    - **Validates: Requirements 2.1, 2.8**

  - [x] 16.4 Write property test: search filters to case-insensitive label matches
    - **Property 5: Search filters to case-insensitive label matches**
    - **Validates: Requirements 2.2**

- [x] 17. Rule Authoring UI: time picker, validation, and save [NEW]
  - [x] 17.1 Implement per-app time picker and daily-limit validation in the ViewModel
    - Extend `RuleAuthoringViewModel`: per-app daily-limit picker defaulting to 30 whole minutes, constrained 1..480; reject non-whole/out-of-range values, retain last valid value, expose a range message ("1–480 whole minutes"); save requires ≥1 app each with a valid limit — otherwise block save, retain selections, expose required message
    - Persist confirmed rules via `InterceptionRuleRepository` and expose save-success state
    - _Requirements: 2.5, 2.6, 2.7, 2.9, 2.10_

  - [x] 17.2 Write property test: daily-limit validation (accept in range, reject-and-retain)
    - **Property 6: Daily-limit validation (accept in range, reject-and-retain otherwise)** — covers create and edit paths
    - **Validates: Requirements 2.6, 2.7, 3.4**

  - [x] 17.3 Write property test: save requires at least one app with a valid limit
    - **Property 7: Save requires at least one app with a valid limit**
    - **Validates: Requirements 2.9, 2.10**

- [x] 18. Rule Authoring UI: existing-rule management (edit/pause/delete) [NEW]
  - [x] 18.1 Implement display, edit, pause, and delete in the ViewModel
    - Extend `RuleAuthoringViewModel`: observe/display existing Rules with limit and enabled/paused state; edit valid limit persists via `updateDailyLimit`; invalid edit rejected with prior value retained + error; pause-for-today via `pauseForToday`; delete via `delete`
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.7_

  - [x] 18.2 Implement `RuleAuthoringScreen` Compose UI with permission rationale and delete confirmation
    - Add `ui/interception/RuleAuthoringScreen.kt`: render app list + search + empty state, time picker, existing-rule list, save/edit/pause controls; delete requires a confirmation dialog; show permission rationale before opening the PACKAGE_USAGE_STATS grant screen
    - Add all Rule_Authoring_Screen and permission-rationale strings (title, search hint, empty-result, time-picker label, range message, save success, required-app message, delete confirmation, rationale) to `res/values`, `res/values-hi`, `res/values-mr`
    - _Requirements: 2.3, 2.5, 3.2, 3.6, 4.2, 4.3_

  - [x] 18.3 Write UI/instrumented tests for authoring screen behaviors
    - Test time-picker default (30), empty-search state, delete confirmation gate, permission-rationale-before-grant ordering
    - _Requirements: 2.5, 2.3, 3.6, 4.3_

- [x] 19. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test tasks and can be skipped for a faster MVP; core implementation tasks are never optional.
- Components labeled [EXISTING] in the design (InterceptionService core loop, AllowanceTracker accumulation, InterceptionConfig base, DeviceCapabilityChecker, InterceptionPermissionHelper, PermissionRevocationDetector, overlay/, reminder/) are NOT rebuilt — only their [MODIFY] deltas are addressed.
- Each test task references a design Correctness Property (1–23) and the requirement ACs it validates; property tests reuse existing `TestFakes`, injectable `Clock` + `ZoneId`, and the `InterceptionContext` fake.
- All new user-facing strings are added to en/hi/mr in the same task that introduces them, per localization rules.
- All Rule operations are scoped to the authenticated `user_id`; raw telemetry never leaves the device.
- The 80% JaCoCo coverage gate applies to the module changes.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "8.1"] },
    { "id": 1, "tasks": ["1.2", "1.3", "2.1", "8.2"] },
    { "id": 2, "tasks": ["2.2", "2.3", "4.1"] },
    { "id": 3, "tasks": ["3.1", "4.2", "4.3", "7.1"] },
    { "id": 4, "tasks": ["3.2", "3.3", "3.4", "3.5", "7.2", "7.3", "7.4", "9.1"] },
    { "id": 5, "tasks": ["6.1", "6.2", "9.2", "9.3", "14.1"] },
    { "id": 6, "tasks": ["6.3", "6.4", "6.5", "6.6", "11.1", "14.2", "14.3"] },
    { "id": 7, "tasks": ["11.2", "12.1", "13.1"] },
    { "id": 8, "tasks": ["11.3", "12.2", "12.3", "16.1"] },
    { "id": 9, "tasks": ["16.2"] },
    { "id": 10, "tasks": ["16.3", "16.4", "17.1"] },
    { "id": 11, "tasks": ["17.2", "17.3", "18.1"] },
    { "id": 12, "tasks": ["18.2"] },
    { "id": 13, "tasks": ["18.3"] }
  ]
}
```
