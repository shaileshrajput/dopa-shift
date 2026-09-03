# Implementation Plan: Dashboard Quick-Create (Goals, Habits, Daily To-Dos)

## Overview

This plan implements the Android Dashboard as a first-class creation surface following the DopaShift multi-module Clean Architecture (`app → [domain, data, ui, sync, interception]`, all feature modules depending on `domain`). Work proceeds bottom-up: pure-Kotlin `domain` models, commands, results, and validators first; then repository/service ports and their `data`-module implementations (Room, bundled APK assets, DataStore, diagnostics aggregation); then use cases (including the atomic `CreateGoalWithHabitUseCase`, `UndoCreationUseCase`, and `ObserveDashboardSummaryUseCase`); then Hilt DI wiring in `app`; then the Compose/Material 3 `ui` (ViewModel, FAB `Quick_Create_Menu`, three `Creation_Sheet`s, Today's Focus quick-add, Zero/Partial state rendering); then correlation/diagnostics; then the additive backend deltas.

The central principle from the design is **no parallel data path**: every Dashboard creation flow drives the same domain use cases, validators, and `Change_Log` emission as the dedicated screens. Property-based tests (Kotest, in-memory fakes) validate the 20 correctness properties; Compose UI, example, and contract/integration tests cover the non-PBT criteria. All tests are tagged with their criterion id (DQC-7.1) and gate CI (DQC-7.8).

All code examples target **Kotlin** (JVM 17), per the tech stack and the design's Kotlin interfaces.

## Tasks

- [x] 1. Establish domain contracts for Dashboard creation
  - [x] 1.1 Define shared domain value types and enums
    - In the Android `domain` module (pure Kotlin, no Android/Room/Hilt imports), add `CreationOrigin` (DASHBOARD, DEDICATED_SCREEN, INTERCEPT_OVERLAY), `DiagnosticEventType` (QUICK_CREATE_OPENED, ENTITY_CREATED, CREATION_ABANDONED, UNDO_INVOKED), `OnboardingStatus` (COMPLETED, SKIPPED, NOT_STARTED), `CorrelationId`, `UndoToken`, and `EntityRef`
    - Reference existing parent domain types (`GoalProfile`, `HabitTrack`, `HabitCheckpoint`, `DailyTodoItem`, `ChangeLogEntry`, `UserId`, `GoalId`) without redefining them
    - _Requirements: 6.3, 1.6, 5.8_

  - [x] 1.2 Define creation commands, `HabitAuthoring`, and `CreationResult`
    - Add `CreateGoalCommand`, `CreateTodoCommand`, `CreateHabitCommand`, `InlineGoalWithHabitCommand`, `GoalWithHabit`
    - Add `HabitAuthoring` sealed interface (Template, Llm, Manual) and `CreationResult<T>` (Success with `UndoToken`, ValidationError, Rejected with retained input, Failure)
    - _Requirements: 2.1, 3.2, 3.5, 4.1, 4.4, 2.11_

  - [x] 1.3 Write unit tests for command/result construction and defaults
    - Verify `origin` defaults to DASHBOARD and `CreationResult` variants carry expected payloads
    - Tag `DQC-2.1`, `DQC-4.1`
    - _Requirements: 2.1, 4.1_

- [x] 2. Implement domain validators (shared usability layer)
  - [x] 2.1 Implement `GoalValidator`
    - Enforce name 1–100 chars and case-insensitive uniqueness per user, category 1–50 chars, 1–20 keywords each 1–50 chars; return per-field error map
    - Expose bound constants (`NAME_MAX`, `CATEGORY_MAX`, `KEYWORD_MAX_LEN`, `KEYWORDS_MAX`)
    - _Requirements: 1.11, 2.1, 2.6, 2.7_

  - [x] 2.2 Implement `TodoValidator`
    - Enforce non-blank text (reject empty/whitespace-only) and max 500 chars; enforce daily cap of 100 via `todayCount`; return per-field error map
    - Expose bound constants (`TEXT_MAX`, `DAILY_CAP`)
    - _Requirements: 4.1, 4.5, 4.6, 4.7_

  - [x] 2.3 Write property test for goal field validation and case-insensitive uniqueness
    - **Property 2: Goal Field Validation and Case-Insensitive Uniqueness**
    - **Validates: Requirements 1.11, 2.1, 2.6**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 2`; boundary values for name/category/keyword counts and lengths plus case-insensitive duplicate names (also satisfies DQC-7.6)

  - [x] 2.4 Write property test for to-do validity invariants
    - **Property 3: To-Do Creation Validity and Invariants**
    - **Validates: Requirements 4.1, 4.3, 4.5, 4.7**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 3`; text bounds, blank rejection, no-goal association, `dayDate` == today in user tz (also satisfies DQC-7.6)

  - [x] 2.5 Write property test for daily to-do cap
    - **Property 4: Daily To-Do Cap Is Never Exceeded**
    - **Validates: Requirements 4.6**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 4`; count never exceeds 100 (also satisfies DQC-7.6)

- [x] 3. Define repository and service ports (domain)
  - [x] 3.1 Declare new domain ports
    - Add `DashboardSummaryRepository` (observe + refresh), `TransactionRunner` (atomic block), `HabitTemplateProvider`, `CategoryKeywordProvider`, `GoalSuggestionService` (with `isConfigured`), and `DiagnosticsEventSink`
    - Reference reused parent ports (`GoalRepository`, `HabitTrackRepository`, `DailyTodoRepository`, `ChangeLogRepository`) without redefining them
    - _Requirements: 5.3, 3.3, 3.5, 2.3, 2.4, 6.4_

  - [x] 3.2 Define `DashboardSummary` and section-state resolution
    - Add `DashboardSummary` with counts, `onboardingStatus`, `isZeroState`, and `sectionState(section)`; add `DashboardSection` and `SectionRenderState` enums
    - _Requirements: 5.3, 5.6_

  - [x] 3.3 Write property test for Dashboard state resolution from counts
    - **Property 16: Dashboard State Resolution From Counts**
    - **Validates: Requirements 5.3, 5.6**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 16`; all-zero ⇒ Zero_State, mixed ⇒ populated + inline prompts, never whole-screen empty when any data (also satisfies DQC-7.3)

- [x] 4. Checkpoint - Ensure all domain-contract and validator tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Implement `data`-module Room and asset infrastructure
  - [x] 5.1 Add `LocalDiagnosticEvent` Room entity and DAO
    - Create the `quick_create_diagnostics` entity (id, eventType, correlationId, entityType, occurredAt) and its DAO; local-only, never synced raw
    - Register the entity/DAO with the existing Room database
    - _Requirements: 6.4_

  - [x] 5.2 Implement `RoomTransactionRunner`
    - Implement `TransactionRunner` over Room's `withTransaction`, ensuring the block runs as one atomic local transaction with rollback on throw
    - _Requirements: 3.3_

  - [x] 5.3 Implement bundled asset providers
    - Package `assets/habit_templates/*.json` (30 checkpoints per template, en/hi/mr) and `assets/category_keywords.json`; implement `AssetHabitTemplateProvider` (`templatesForCategory`, `defaultTemplate`) and `AssetCategoryKeywordProvider` (`presetCategories`, `suggestedKeywords`) reading these assets offline
    - Preset categories at minimum: Career Growth, Fitness, Build Business, Clear Exam, Learning, Wellbeing
    - _Requirements: 2.2, 2.3, 3.5, 3.6_

  - [x] 5.4 Implement `RoomDashboardSummaryRepository`
    - Implement `observe(userId)` as a `Flow<DashboardSummary>` combining active-goal, today-to-do, and active-habit counts plus onboarding status from local stores; implement `refresh(userId)` to reconcile from the backend summary endpoint
    - Scope every query by the authenticated `user_id`
    - _Requirements: 5.3, 1.5, 2.9_

  - [x] 5.5 Write unit tests for asset providers and summary repository
    - Verify preset categories, per-category keyword suggestions, template checkpoint counts, and summary count aggregation
    - Tag `DQC-2.2`, `DQC-2.3`, `DQC-5.3`
    - _Requirements: 2.2, 2.3, 5.3_

- [x] 6. Implement DataStore prefs and diagnostics aggregation
  - [x] 6.1 Implement `QuickCreatePrefs` DataStore
    - Persist `onboardingStatus`, `resumeOnboardingDismissed`, and `lastDiagnosticsSyncAt`; expose read/update APIs; never re-present onboarding after completion/skip
    - _Requirements: 5.8, 5.9, 6.5_

  - [x] 6.2 Implement `DataStoreDiagnosticsSink` and `DiagnosticsAggregator`
    - Implement `DiagnosticsEventSink.record(...)` writing structured local events (with event-type + Correlation_ID) to Room; implement the aggregator that rolls events into per-type counts, enforces ≤10 KB payload and ≤1×/24 h via `lastDiagnosticsSyncAt`, syncs counts-only through the `Sync_Engine`, and never syncs raw records
    - Queue offline and flush on reconnect via the standard retry policy
    - _Requirements: 6.4, 6.5, 6.6_

  - [x] 6.3 Write property test for one origin Correlation_ID and correct event type
    - **Property 17: One Origin Correlation_ID and Correct Event Type per Action**
    - **Validates: Requirements 6.3, 6.4**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 17`

  - [x] 6.4 Write property test for aggregated, bounded diagnostics sync
    - **Property 18: Diagnostics Sync Is Aggregated and Bounded**
    - **Validates: Requirements 6.5**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 18`; counts-only, ≤10 KB, ≤1×/24 h

- [x] 7. Checkpoint - Ensure all data-layer tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 8. Implement single-entity creation use cases (same path as dedicated screens)
  - [x] 8.1 Wire Dashboard to reused `CreateGoalUseCase`, `CreateDailyTodoUseCase`, `CreateHabitTrackUseCase`
    - Confirm the Dashboard invokes the same parent use-case instances (no reimplementation); ensure each validates via the shared validators, writes local Room within 200 ms, and enqueues create `Change_Log` events regardless of connectivity; return `CreationResult.Success` carrying an `UndoToken`
    - _Requirements: 1.10, 6.1, 2.8, 4.2, 4.9_

  - [x] 8.2 Write property test for Dashboard/dedicated-screen equivalence
    - **Property 1: Dashboard and Dedicated-Screen Creation Are Equivalent**
    - **Validates: Requirements 1.10, 6.1**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 1`; same accept/reject outcome and same Change_Log events for DASHBOARD vs DEDICATED_SCREEN origin (satisfies DQC-7.1)

  - [x] 8.3 Write property test for offline local persistence and Change_Log enqueue
    - **Property 5: Offline Local Persistence and Change_Log Enqueue**
    - **Validates: Requirements 2.8, 4.2, 4.9**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 5`; local write ≤200 ms + create events enqueued offline (satisfies DQC-7.5)

  - [x] 8.4 Write property test for to-do completion toggle round-trip
    - **Property 20: To-Do Completion Toggle Round-Trip**
    - **Validates: Requirements 4.8**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 20`

- [x] 9. Implement habit authoring resolution
  - [x] 9.1 Implement authoring-mode resolver to exactly 30 checkpoints
    - Resolve Template / LLM / Manual authoring to exactly 30 `HabitCheckpoint`s (1–200 chars each); for manual < 30, auto-fill remaining days with the last authored description; set start date to today in user tz and current-day pointer to 1
    - Inform the user which track takes precedence if an active track already exists for the goal; offer (not require) a daily checkpoint Reminder
    - _Requirements: 3.6, 3.9, 3.10, 3.11, 3.12_

  - [x] 9.2 Implement LLM habit-plan and goal-suggestion flow with graceful degradation
    - Use `GoalSuggestionService`: when unconfigured, callers disable LLM mode and default to Template; on failure/timeout, fall back to Template, surface the reason non-blockingly, and retain already-entered input; send only goal name/description/keywords in any AI request
    - _Requirements: 2.4, 2.5, 3.7, 3.8_

  - [x] 9.3 Write property test for habit authoring resolving to 30 valid checkpoints
    - **Property 10: Habit Authoring Resolves to Exactly 30 Valid Checkpoints**
    - **Validates: Requirements 3.6, 3.9**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 10` (also satisfies DQC-7.6)

  - [x] 9.4 Write property test for habit-create invariants
    - **Property 11: Habit-Create Invariants**
    - **Validates: Requirements 3.10**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 11`; startDate == today(tz), currentDay == 1

  - [x] 9.5 Write property test for LLM fallback without losing input
    - **Property 12: LLM Failure Falls Back to Template Without Losing Input**
    - **Validates: Requirements 3.8**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 12`

  - [x] 9.6 Write property test for LLM context privacy boundary
    - **Property 13: LLM Context Privacy Boundary**
    - **Validates: Requirements 2.4**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 13`; payload ⊆ {name, description, keywords}, never telemetry/task history

  - [x] 9.7 Write property test for preset-category bundled keyword suggestions
    - **Property 14: Preset Category Yields Bundled Suggested Keywords**
    - **Validates: Requirements 2.3**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 14`

- [x] 10. Implement `CreateGoalWithHabitUseCase` (Inline_Goal_Capture atomic transaction)
  - [x] 10.1 Implement `CreateGoalWithHabitInteractor`
    - Run inside `TransactionRunner.inTransaction { }`: validate goal (same `GoalValidator`), resolve 30 checkpoints, persist `GoalProfile`, then persist `HabitTrack` (+30 checkpoints) referencing the just-saved `goalId`; on any failure roll back the whole unit so zero goals and zero habits persist and no Change_Log events for the aborted unit are committed
    - Append `Change_Log` events within the transaction in fixed order: all goal `create` events first, then all habit `create` (and checkpoint) events; never write a `HabitTrack` with a missing/unsaved `goalId`
    - Return `Success(GoalWithHabit, undoToken=[goal, habit])` sharing one Correlation_ID across the transaction
    - _Requirements: 3.2, 3.3, 3.4, 6.2, 1.6_

  - [x] 10.2 Write property test for Inline_Goal_Capture atomic rollback
    - **Property 6: Inline_Goal_Capture Atomic Rollback**
    - **Validates: Requirements 3.3**
    - Kotest, 100+ iterations with a fault-injecting fake habit repository, tag `Feature: dashboard-quick-create, Property 6`; injected habit failure ⇒ 0 goals + 0 habits persisted, no committed events (satisfies DQC-7.2)

  - [x] 10.3 Write property test for no-orphan invariant across creation paths
    - **Property 7: No-Orphan Invariant Across All Creation Paths**
    - **Validates: Requirements 3.4**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 7`; store never contains a habit referencing a missing/unsaved goal (satisfies DQC-7.2)

  - [x] 10.4 Write property test for Change_Log goal-before-dependent ordering
    - **Property 8: Change_Log Ordering — Goal Before Dependent**
    - **Validates: Requirements 6.2**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 8`; every goal event precedes every dependent habit/checkpoint event (satisfies DQC-7.7)

  - [x] 10.5 Write property test for default habit goal selection
    - **Property 15: Default Habit Goal Selection Is Most-Recently-Updated**
    - **Validates: Requirements 3.1**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 15`

- [x] 11. Implement `UndoCreationUseCase` and `ObserveDashboardSummaryUseCase`
  - [x] 11.1 Implement `UndoCreationUseCase`
    - Delete each `EntityRef` in the `UndoToken` through the standard delete path so a `Change_Log` deletion event flows through the `Sync_Engine` (never a local-only rollback); for an Inline_Goal_Capture token, revert the whole transaction (goal + habit)
    - _Requirements: 1.7_

  - [x] 11.2 Implement `ObserveDashboardSummaryUseCase`
    - Return `Flow<DashboardSummary>` from `DashboardSummaryRepository` so each successful creation refreshes state and the Dashboard reflects the new entity within 1 second; suppress Zero_State prompts for entity types created during onboarding
    - _Requirements: 5.3, 5.7, 1.5, 2.9_

  - [x] 11.3 Write property test for undo emitting a Change_Log deletion event
    - **Property 9: Undo Emits a Change_Log Deletion Event**
    - **Validates: Requirements 1.7**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 9`; assert a deletion event is emitted, not a local rollback (satisfies DQC-7.4)

  - [x] 11.4 Write property test for backend rejection retaining input
    - **Property 19: Backend Rejection Retains Input**
    - **Validates: Requirements 2.11**
    - Kotest, 100+ iterations, tag `Feature: dashboard-quick-create, Property 19`

- [x] 12. Checkpoint - Ensure all use-case and property tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 13. Wire Hilt DI in the `app` module
  - [x] 13.1 Bind repositories and providers
    - Add `QuickCreateRepositoryModule` binding `DashboardSummaryRepository`, `TransactionRunner`, `HabitTemplateProvider`, `CategoryKeywordProvider`, and `DiagnosticsEventSink` to their `data` implementations
    - _Requirements: 5.3, 3.3, 2.3, 6.4_

  - [x] 13.2 Provide use cases
    - Add `QuickCreateUseCaseModule` providing `CreateGoalWithHabitUseCase` (via `CreateGoalWithHabitInteractor` with goal/habit/change-log repos + `TransactionRunner`); reuse parent-provided `CreateGoalUseCase`/`CreateDailyTodoUseCase`/`CreateHabitTrackUseCase`; provide `UndoCreationUseCase` and `ObserveDashboardSummaryUseCase`
    - _Requirements: 1.10, 3.3, 1.7_

- [x] 14. Implement `DashboardViewModel` and creation state holders (`ui`)
  - [x] 14.1 Implement `DashboardViewModel`
    - Inject all use cases, diagnostics sink, template/keyword/suggestion providers, and a `CorrelationIdFactory`; expose `summary`, `activeSheet`, and `snackbar` state; `openQuickCreate()` generates the Correlation_ID at origin and records `QUICK_CREATE_OPENED`; `saveGoal/saveTodo/saveHabit` route to the same use cases the dedicated screens use; `onUndo(token)` calls `UndoCreationUseCase`; `dismissSheetWithUnsavedInput()` triggers confirm-discard and records `CREATION_ABANDONED`
    - _Requirements: 1.5, 1.6, 1.7, 1.9, 6.3, 6.4_

  - [x] 14.2 Write unit tests for ViewModel routing and state transitions
    - Verify save routing, undo dispatch, snackbar emission ≥5 s, and abandonment recording
    - Tag `DQC-1.6`, `DQC-6.3`, `DQC-6.4`
    - _Requirements: 1.6, 6.3, 6.4_

- [x] 15. Implement Dashboard creation UI (Compose + Material 3)
  - [x] 15.1 Implement persistent FAB and `QuickCreateMenu`
    - Render a FAB reachable without scrolling in every scroll position, window size class, and both Zero/Partial state; expand to exactly three labeled+iconed actions in order New Goal, New Habit, New To-Do; present the corresponding sheet within 300 ms; dismiss via back gesture, scrim tap, or FAB re-tap without creating anything
    - _Requirements: 1.1, 1.2, 1.3, 1.8_

  - [x] 15.2 Implement `GoalCreationSheet`
    - Collect name/category/keywords with inline validation and character counters within 20 of limits; preset category picker plus free-text; pre-populate and allow edit/remove of suggested keywords; show or disable "Suggest with AI" based on `isConfigured`; on first goal, transition out of Zero_State and prompt to start a habit
    - _Requirements: 2.1, 2.2, 2.3, 2.5, 2.6, 2.7, 2.9, 2.10_

  - [x] 15.3 Implement `HabitCreationSheet` with Inline_Goal_Capture step
    - When ≥1 active goal, first step is goal selection defaulting to most-recently-updated; when zero goals, first step is Inline_Goal_Capture (no blocking error, no navigation away); offer template/LLM/manual authoring modes with disclosures; route save to `CreateHabitTrackUseCase` or `CreateGoalWithHabitUseCase`
    - _Requirements: 3.1, 3.2, 3.5, 3.7, 3.9, 3.11, 3.12, 5.4_

  - [x] 15.4 Implement `TodoCreationSheet` and `TodaysFocusQuickAdd`
    - Full sheet collects text plus optional due date/time and Reminder; inline quick-add creates a to-do from text only, persists within 200 ms, clears input, retains keyboard focus, and prepends to the list; enforce 500-char limit, 100/day cap, and ignore empty/whitespace; support strikethrough + 50% opacity completion with un-check; function offline with sync-pending indicator
    - _Requirements: 1.4, 4.2, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9_

  - [x] 15.5 Implement `DashboardBody` Zero/Partial state rendering and onboarding resume
    - Render whole-screen three-prompt Zero_State only when all counts are zero; render populated sections normally and only empty sections as inline prompts in Partial_State; keep goal-dependent sections visible as inline prompts; add a "Skip" onboarding affordance landing on Zero_State and a dismissible Settings "resume onboarding" entry whose dismissal is remembered
    - _Requirements: 5.1, 5.2, 5.3, 5.6, 5.9_

- [x] 16. Integrate interception overlay entry point
  - [x] 16.1 Reach Inline_Goal_Capture from the Intercept_Overlay
    - In the `interception` module, invoke `CreateGoalWithHabitUseCase` so a zero-goal user intercepted by the overlay can reach Inline_Goal_Capture (origin INTERCEPT_OVERLAY), reusing the same use case with no parallel path
    - _Requirements: 5.10_

- [x] 17. Implement additive backend deltas
  - [x] 17.1 Add nested `habitTrack` payload to `POST /v1/goals`
    - Accept an optional nested `habitTrack` committed in one server transaction for Inline_Goal_Capture server-side atomicity; keep the endpoint additive so existing payloads remain valid; reject any habit/rule with absent/invalid `goalId` (API-layer invariant); scope by authenticated `user_id`
    - _Requirements: 3.3, 5.5_

  - [x] 17.2 Add `GET /v1/dashboard/summary`
    - Return per-entity-type counts (`activeGoalCount`, `todayTodoCount`, `activeHabitCount`) sufficient to distinguish Zero_State from Partial_State in one response; scope by authenticated `user_id`
    - _Requirements: 5.3_

  - [x] 17.3 Write contract tests for the backend deltas
    - Assert API-layer `goalId` rejection and conflict surfacing; assert summary counts shape
    - Tag `DQC-5.5`, `DQC-6.7`, `DQC-5.3`
    - _Requirements: 5.5, 6.7, 5.3_

- [x] 18. Author Compose UI, example, and integration tests for non-PBT criteria
  - [x] 18.1 Write Compose UI tests for Dashboard renderings and affordances
    - Cover Zero_State/Partial_State/populated renderings, FAB/menu content and order, sheet presentation, undo affordance visibility and dismissal, confirm-before-discard, and onboarding skip/resume
    - Tag `DQC-1.1`, `DQC-1.2`, `DQC-1.4`, `DQC-1.5`, `DQC-1.6`, `DQC-1.8`, `DQC-1.9`, `DQC-5.1`, `DQC-5.2`, `DQC-5.6`, `DQC-5.7`, `DQC-5.9`, `DQC-5.10` (DQC-7.3)
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 1.8, 1.9, 5.1, 5.2, 5.6, 5.7, 5.9, 5.10_

  - [x] 18.2 Write example tests for sheet content, modes, and disclosures
    - Cover goal sheet content and required-field minimum, habit sheet modes/disclosures, to-do full sheet, and LLM-disabled state
    - Tag `DQC-2.2`, `DQC-2.5`, `DQC-2.7`, `DQC-2.9`, `DQC-2.10`, `DQC-3.2`, `DQC-3.5`, `DQC-3.7`, `DQC-3.11`, `DQC-3.12`, `DQC-4.4`, `DQC-5.4`
    - _Requirements: 2.2, 2.5, 2.7, 2.9, 2.10, 3.2, 3.5, 3.7, 3.11, 3.12, 4.4, 5.4_

  - [x] 18.3 Write timing and offline integration tests
    - Assert sheet presentation ≤300 ms, Dashboard reflect ≤1 s, offline diagnostics flush on reconnect, and onboarding persistence across launches
    - Tag `DQC-1.3`, `DQC-1.5`, `DQC-6.6`, `DQC-5.8`
    - _Requirements: 1.3, 1.5, 6.6, 5.8_

  - [x] 18.4 Write sync-conflict surfacing test
    - Assert a Dashboard-created entity superseded/rejected on sync is surfaced non-blockingly and the losing edit retained in conflict history
    - Tag `DQC-6.7`
    - _Requirements: 6.7_

- [x] 19. Configure CI gate for the spec test suites
  - [x] 19.1 Register all suites in CI and enforce the gate
    - Ensure the property, Compose UI, example, contract, and integration suites run in CI; fail the build on any failing test so no criterion is marked complete on a red build; treat atomicity (DQC-7.2), Change_Log ordering (DQC-7.7), undo-deletion (DQC-7.4), offline-persistence (DQC-7.5), and boundary-value (DQC-7.6) tests as blocking
    - _Requirements: 7.1, 7.8_

- [x] 20. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test tasks and can be skipped for a faster MVP; core implementation tasks are never marked optional.
- Each task references specific requirement acceptance criteria (DQC-N.M) for traceability.
- Property-based tests use Kotest with 100+ iterations and in-memory fakes (a fault-injecting fake for the atomicity/rollback property), tagged `Feature: dashboard-quick-create, Property {N}`.
- The 20 correctness properties each map to exactly one property-based test; the critical properties (P5, P6, P7, P8, P9, P13, P18) have dedicated tasks.
- The `domain` module stays pure Kotlin; ports are implemented in `data`; Compose lives in `ui`; wiring is in `app`; the overlay reuses the same Inline_Goal_Capture use case (no parallel path).
- Checkpoints ensure incremental validation at layer boundaries.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["2.1", "2.2", "3.1", "3.2"] },
    { "id": 3, "tasks": ["2.3", "2.4", "2.5", "3.3", "5.1", "5.2", "5.3", "6.1"] },
    { "id": 4, "tasks": ["5.4", "6.2"] },
    { "id": 5, "tasks": ["5.5", "6.3", "6.4", "8.1", "9.1", "9.2"] },
    { "id": 6, "tasks": ["8.2", "8.3", "8.4", "9.3", "9.4", "9.5", "9.6", "9.7", "10.1"] },
    { "id": 7, "tasks": ["10.2", "10.3", "10.4", "10.5", "11.1", "11.2"] },
    { "id": 8, "tasks": ["11.3", "11.4", "13.1", "13.2"] },
    { "id": 9, "tasks": ["14.1"] },
    { "id": 10, "tasks": ["14.2", "15.1", "15.2", "15.3", "15.4", "15.5", "16.1"] },
    { "id": 11, "tasks": ["17.1", "17.2"] },
    { "id": 12, "tasks": ["17.3", "18.1", "18.2", "18.3", "18.4"] },
    { "id": 13, "tasks": ["19.1"] }
  ]
}
```
