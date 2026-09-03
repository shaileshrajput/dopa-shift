# Requirements Document

## Dashboard Quick-Create: Goals, Habits, and Daily To-Dos

**Spec ID:** `dashboard-quick-create`
**Platform:** Android_App
**Version:** 1.0
**Date:** 2026-09-01
**Parent spec:** `.kiro/specs/dopa-shift/requirements.md`, `.kiro/specs/dopa-shift/design.md`
**Sibling specs:** `.kiro/specs/dynamic-ui-experience/`, `.kiro/specs/screen-time-interception-engine/`
**Status:** Draft for review

---

## Introduction

This specification makes the DopaShift Dashboard (home screen) a first-class **creation** surface on Android. A user SHALL be able to create a Goal_Profile, a Habit_Track, and a DailyTodoItem directly from the Dashboard — including the case where the user **skipped or partially completed onboarding and therefore has zero Goal_Profiles**.

This is a child spec. It inherits every requirement of the parent `dopa-shift` spec unless it explicitly overrides one, and every override is recorded in the [Conflict Resolution Log](#conflict-resolution-log).

### Problem being solved

The parent specification contains a dead end:

- **Requirement 1, AC4** blocks creation of Habit_Tracks and interception rules when the user has zero active goals.
- **Requirement 17, AC4** assumes onboarding always runs to completion and produces a goal.
- **Requirement 20, AC11** already anticipates a user with zero goals, zero to-dos, and zero habits reaching the Dashboard.

Taken together, a user who skips onboarding lands on a Dashboard where the two prompts the parent spec requires ("start a habit", "create your first goal") lead to a blocking error. This spec closes that gap **without weakening the parent's no-orphan-references invariant** (Requirement 1, AC6): the Android client creates the required goal inline, rather than bypassing the constraint.

---

## Scope

**In scope:** Android_App creation entry points on the Dashboard; the Creation_Sheet flows for Goal, Habit, and DailyTodoItem; zero-goal and partial-state handling; undo; the sync and traceability behavior of these flows.

**Out of scope:** Web_Portal parity for these surfaces (tracked separately); visual, motion, and accessibility standards (see `dynamic-ui-experience`); interception-rule authoring (see `screen-time-interception-engine`); new Backend endpoints beyond the additive deltas listed in [Backend Deltas](#backend-deltas).

---

## Assumptions

1. **Onboarding is skippable.** The parent spec caps onboarding at 5 screens but never states whether it can be exited early. This spec assumes it can, and that skipping is a supported, non-degraded path.
2. **A Habit_Track always belongs to exactly one Goal_Profile.** This spec does not introduce goal-less habits; it introduces *inline goal capture* so the user never leaves the Dashboard to satisfy that constraint.
3. **Bundled content ships in the APK.** Category-to-keyword mappings and 30-day habit templates are packaged with the app so creation works fully offline; no endpoint is required in v1.
4. **Minimum supported Android API level is 26.**

---

## Glossary

_Additions to the parent glossary._

- **Quick_Create_Menu**: The expandable creation affordance anchored to the Dashboard's floating action button, exposing exactly three actions: New Goal, New Habit, New To-Do.
- **Creation_Sheet**: A modal bottom sheet hosting a creation form, presented over the Dashboard without a full-screen navigation transition.
- **Inline_Goal_Capture**: A compact, embedded goal-creation step rendered inside a dependent entity's Creation_Sheet when the user has zero Goal_Profiles, producing a valid Goal_Profile before the dependent entity is committed.
- **Zero_State**: The Dashboard rendering used when the user has zero Goal_Profiles, zero DailyTodoItems, and zero Habit_Tracks (parent Requirement 20, AC11).
- **Partial_State**: A Dashboard rendering where at least one — but not all — of {Goal_Profile, DailyTodoItem, Habit_Track} has data.

---

## Wireframe References

| Screen | Wireframe Path | Requirements |
|---|---|---|
| Dashboard | `.kiro/specs/wireframes/dashboard/code.html` | DQC-1, DQC-5 |
| Create New Goal | `.kiro/specs/wireframes/create_new_goal/code.html` | DQC-2 |
| Habit Roadmap | `.kiro/specs/wireframes/habit_roadmap/code.html` | DQC-3 |
| Daily Tasks | `.kiro/specs/wireframes/daily_tasks/code.html` | DQC-4 |

---

## Requirements

### Requirement 1: [DQC-1] Dashboard Quick-Create Entry Points

**User Story:** As a user, I want to create a goal, a habit, or a to-do item directly from the home screen, so that I can capture an intention the moment I have it without hunting through navigation.

#### Acceptance Criteria

1. THE Android_App SHALL render a persistent floating action button (FAB) on the Dashboard that is reachable without scrolling in every scroll position, in every window size class, and in both Zero_State and Partial_State.
2. WHEN the user activates the Dashboard FAB THEN THE Android_App SHALL expand a Quick_Create_Menu presenting exactly three actions in this order: "New Goal", "New Habit", "New To-Do" — each with an icon **and** a text label, never icon-only.
3. WHEN the user selects any Quick_Create_Menu action THEN THE Android_App SHALL present the corresponding Creation_Sheet within 300 milliseconds of touch-up, measured to the sheet's first rendered frame.
4. THE Android_App SHALL additionally provide an inline quick-add text input within the Dashboard's "Today's Focus" section that creates a DailyTodoItem without opening a Creation_Sheet (parent Requirement 20, AC14).
5. WHEN an entity is successfully created from any Dashboard entry point THEN THE Android_App SHALL dismiss the Creation_Sheet, remain on the Dashboard, and reflect the new entity in the relevant Dashboard section within 1 second — the user SHALL NOT be navigated away from the Dashboard.
6. WHEN an entity is created from the Dashboard THEN THE Android_App SHALL display a transient confirmation containing an "Undo" action that remains available for at least 5 seconds.
7. IF the user activates "Undo" THEN THE Android_App SHALL delete the just-created entity and emit the corresponding Change_Log deletion event through the standard Sync_Engine pipeline (parent Requirement 4, AC4–AC5) — undo SHALL NOT be a local-only rollback.
8. THE Quick_Create_Menu SHALL be dismissible by back gesture, scrim tap, or FAB re-tap, and dismissal SHALL NOT create any entity.
9. IF a Creation_Sheet contains unsaved user input AND the user attempts to dismiss it THEN THE Android_App SHALL prompt for confirmation before discarding that input.
10. THE Android_App SHALL make every entity creatable from the Dashboard also creatable from its dedicated screen (Goals, Habits, Daily Tasks) using the same underlying use case and the same validation rules — the Dashboard path SHALL NOT be a parallel implementation.
11. THE Android_App SHALL enforce, in every Dashboard creation flow, the identical field-level validation rules defined by the parent spec for that entity type — client-side validation SHALL be a usability layer over server-side validation, never a replacement for it (parent Requirement 22, AC7).

---

### Requirement 2: [DQC-2] Goal Creation from the Dashboard

**User Story:** As a user with no goals, I want to create my first goal from the home screen in under a minute, so that the rest of the app unlocks immediately.

#### Acceptance Criteria

1. WHEN the user selects "New Goal" from the Quick_Create_Menu THEN THE Android_App SHALL present a Creation_Sheet collecting: goal name (required, 1–100 characters, unique per user), category (required, 1–50 characters), and keywords (required, minimum 1, maximum 20, each 1–50 characters) — matching parent Requirement 1, AC1–AC2 exactly.
2. THE goal Creation_Sheet SHALL offer a preset category picker containing at minimum Career Growth, Fitness, Build Business, Clear Exam, Learning, and Wellbeing, AND SHALL allow free-text entry of a category not in the preset list.
3. WHEN the user selects a preset category THEN THE Android_App SHALL pre-populate suggested keywords for that category from a locally bundled mapping, and the user SHALL be able to accept, edit, or individually remove each suggestion before saving.
4. IF an LLM_Provider is configured (parent Requirement 15) THEN THE Android_App SHALL offer an optional "Suggest with AI" action that requests keyword and description suggestions, sending only goal name, goal description, and user-supplied keywords as context (parent Requirement 15, AC7).
5. IF no LLM_Provider is configured THEN THE Android_App SHALL hide or visibly disable the "Suggest with AI" action with an explanatory label, rather than presenting an action that errors on tap (parent Requirement 15, AC6).
6. IF the user enters a goal name that duplicates an existing goal name for that user (case-insensitive) THEN THE Android_App SHALL display an inline validation error on the name field before submission is attempted, and SHALL disable the save action while that error is present (parent Requirement 1, AC7).
7. THE Android_App SHALL validate name length, category length, keyword count, and keyword length inline as the user types, displaying a per-field character counter when the field is within 20 characters of its limit.
8. WHEN the user saves a valid goal THEN THE Android_App SHALL persist it to the local Room store within 200 milliseconds and enqueue the corresponding Change_Log events, regardless of connectivity (parent Requirement 4, AC4).
9. WHEN a goal is created from the Dashboard AND it is the user's first Goal_Profile THEN THE Android_App SHALL transition the Dashboard out of Zero_State and present a contextual next-step prompt to start a Habit_Track for that goal.
10. THE goal Creation_Sheet SHALL be completable with no more than three required inputs (name, category, at least one keyword) — no additional field SHALL be mandatory in v1.
11. IF goal creation is rejected by the Backend on sync (for example, a name uniqueness collision created on another device while offline) THEN THE Android_App SHALL retain the user's input, surface the specific rejection reason, and offer correction — the goal data SHALL NOT be silently discarded.

---

### Requirement 3: [DQC-3] Habit Creation from the Dashboard Without a Pre-existing Goal

**User Story:** As a user who skipped onboarding and has no goals, I want to start a habit from the home screen, so that a missing goal does not block me from doing the one thing I opened the app to do.

#### Acceptance Criteria

1. WHEN the user selects "New Habit" AND the user has one or more active Goal_Profiles THEN THE Android_App SHALL present a Creation_Sheet whose first step is goal selection, defaulting to the most recently updated active goal.
2. WHEN the user selects "New Habit" AND the user has **zero** active Goal_Profiles THEN THE Android_App SHALL present Inline_Goal_Capture as the first step of that same Creation_Sheet, collecting the minimum valid goal fields defined in DQC-2, AC1 — THE Android_App SHALL NOT display a blocking error and SHALL NOT navigate the user to a separate goal screen.
3. WHEN Inline_Goal_Capture is completed THEN THE Android_App SHALL create the Goal_Profile and the Habit_Track as a single atomic local transaction — IF Habit_Track creation fails for any reason THEN THE Android_App SHALL roll back the goal creation so no partially created state is persisted.
4. THE Android_App SHALL NOT persist a Habit_Track referencing a non-existent or unsaved Goal_Profile at any point, upholding the parent's no-orphan-references invariant (parent Requirement 1, AC6).
5. THE habit Creation_Sheet SHALL offer at minimum three authoring modes: (a) a bundled 30-day template appropriate to the goal category, (b) an LLM-generated 30-day plan, and (c) manual checkpoint authoring.
6. IF the user selects template mode THEN THE Android_App SHALL populate all 30 checkpoints with descriptions of 1–200 characters each and SHALL allow editing of any individual checkpoint before saving (parent Requirement 6, AC1).
7. IF the user selects LLM mode AND no LLM_Provider is configured THEN THE Android_App SHALL disable that mode with an explanatory label and default the selection to template mode.
8. IF the user selects LLM mode AND the LLM call fails or times out THEN THE Android_App SHALL fall back to template mode, show the failure reason non-blockingly, and SHALL NOT discard input the user has already entered in the sheet (parent Requirement 15, AC10).
9. IF the user selects manual mode AND saves with fewer than 30 authored checkpoints THEN THE Android_App SHALL auto-fill the remaining days with the last authored checkpoint description AND SHALL disclose this behavior to the user before saving.
10. WHEN a Habit_Track is created THEN THE Android_App SHALL set its start date to the current date in the user's configured time zone, set its current-day pointer to 1, and render its day-1 checkpoint on the Dashboard immediately (parent Requirement 20, AC2b).
11. IF an active Habit_Track already exists for the selected goal THEN THE Android_App SHALL inform the user which track will take precedence in the Intercept_Overlay before saving the new one (parent Requirement 6, AC5).
12. WHEN a Habit_Track is created THEN THE Android_App SHALL offer, without requiring, a daily Reminder for its checkpoint using the shared Reminder model (parent Requirement 5, AC1).

---

### Requirement 4: [DQC-4] Daily To-Do Creation from the Dashboard

**User Story:** As a user, I want to add a to-do in one tap and one line of typing from the home screen, so that capture friction never causes me to skip writing it down.

#### Acceptance Criteria

1. THE Android_App SHALL create a DailyTodoItem from the Dashboard inline quick-add input using only text as required input — a DailyTodoItem SHALL NOT require an associated goal (parent Requirement 4, AC1).
2. WHEN the user submits the inline quick-add input THEN THE Android_App SHALL persist the DailyTodoItem within 200 milliseconds, clear the input, retain keyboard focus for a consecutive entry, and prepend the item to the "Today's Focus" list.
3. WHEN a DailyTodoItem is created THEN THE Android_App SHALL assign it a `dayDate` equal to the current date in the user's configured time zone.
4. WHEN the user opens the full "New To-Do" Creation_Sheet THEN THE Android_App SHALL additionally collect an optional due date/time and an optional Reminder configuration (parent Requirement 5, AC1).
5. IF the entered to-do text exceeds 500 characters THEN THE Android_App SHALL block submission and display an inline error stating the maximum length (parent Requirement 4, AC12).
6. IF the user already has 100 DailyTodoItems for the current day THEN THE Android_App SHALL disable to-do creation for that day and display a message stating the daily limit (parent Requirement 4, AC2).
7. IF the user submits empty or whitespace-only text THEN THE Android_App SHALL take no action and SHALL NOT create an item.
8. WHEN a DailyTodoItem is completed from the Dashboard THEN THE Android_App SHALL apply the `label-strikethrough` type style, reduce the row to 50% opacity, and retain the item in the completed view with the ability to un-check it (parent Requirement 4, AC3).
9. THE quick-add input SHALL remain functional and SHALL persist items locally while the device is offline, with no visible difference in behavior other than the sync-pending indicator (parent Requirement 8, AC4).

---

### Requirement 5: [DQC-5] Zero-Goal and Skipped-Onboarding State Handling

**User Story:** As a user who skipped onboarding, I want the home screen to guide me rather than block me, so that I can start using the app on my own terms.

#### Acceptance Criteria

1. THE Android_App SHALL allow the user to exit the onboarding flow at any screen via a visible "Skip" affordance and SHALL land the user on the Dashboard in Zero_State — this explicitly extends parent Requirement 17, AC4.
2. WHEN the Dashboard is rendered in Zero_State THEN THE Android_App SHALL display three contextual creation prompts — create a goal, add a to-do, start a habit — each directly activating the corresponding Creation_Sheet (parent Requirement 20, AC11).
3. WHEN the Dashboard is rendered in Partial_State THEN THE Android_App SHALL render populated sections normally and render only the empty sections as inline prompts — a whole-screen empty state SHALL NOT be rendered when any entity type has data.
4. IF the user has zero active Goal_Profiles AND initiates an action requiring a goal reference (start a Habit_Track, create an interception rule) THEN THE Android_App SHALL present Inline_Goal_Capture inside that action's flow rather than the blocking error described in parent Requirement 1, AC4.
5. THE blocking behavior of parent Requirement 1, AC4 SHALL remain fully in force at the Backend/API layer: a request to create a Habit_Track or interception rule with an absent or invalid `goalId` SHALL be rejected. The client satisfies the constraint by creating the goal first, never by bypassing it.
6. WHILE the user has zero active Goal_Profiles THE Android_App SHALL render goal-dependent Dashboard sections (Active Goals grid, habit checkpoint card) as inline prompts rather than hiding them, so the capability remains discoverable.
7. WHEN the user completes onboarding normally THEN THE Android_App SHALL NOT show Zero_State prompts for entity types already created during onboarding.
8. THE Android_App SHALL persist onboarding completion/skip status locally and SHALL NOT re-present the onboarding flow on subsequent launches after either outcome.
9. IF the user skipped onboarding THEN THE Android_App SHALL surface a dismissible entry point to resume onboarding from Settings, and its dismissal SHALL be remembered across launches.
10. WHEN a user with zero Goal_Profiles is intercepted by the Intercept_Overlay THEN Inline_Goal_Capture SHALL be reachable from within the overlay, per `screen-time-interception-engine` STI-4, AC5.

---

### Requirement 6: [DQC-6] Sync, Traceability, and Auditing of Dashboard Creation

**User Story:** As the system owner, I want dashboard-originated creation to be as traceable and sync-safe as creation from any other screen, so that there are no diagnostic blind spots and no divergent data paths.

#### Acceptance Criteria

1. WHEN any entity is created from a Dashboard entry point THEN THE Android_App SHALL emit Change_Log events through the existing Sync_Engine with no separate code path (parent Requirement 4, AC5).
2. WHEN Inline_Goal_Capture creates a Goal_Profile and a dependent entity in one transaction THEN THE Android_App SHALL emit the goal's Change_Log events **before** the dependent entity's, so that replay on another device never produces a temporarily orphaned reference.
3. WHEN a user action originates on the Dashboard or in a Creation_Sheet THEN THE Android_App SHALL generate a Correlation_ID at the point of origin and propagate it through every downstream call and local write for that action (parent Requirement 18, AC1).
4. THE Android_App SHALL log structured local events for: quick-create opened, entity created, creation abandoned, and undo invoked — each including the Correlation_ID and a machine-parseable event-type field (parent Requirement 18, AC2, AC9).
5. THE Android_App SHALL sync only aggregated diagnostic summaries of these events — counts, never raw records — at most once per 24 hours with a maximum payload of 10 KB (parent Requirement 18, AC9).
6. WHILE offline THE Android_App SHALL queue all events from these flows and sync them on reconnect using the standard retry policy (parent Requirement 4, AC10).
7. WHEN an entity created on the Dashboard is superseded or rejected by conflict resolution on sync THEN THE Android_App SHALL surface the outcome to the user non-blockingly and retain the losing edit in conflict history (parent Requirement 4, AC7).

---

### Requirement 7: [DQC-7] Verification of This Specification

**User Story:** As the system owner, I want each acceptance criterion here to be independently verifiable, so that implementation cannot report completion without evidence.

#### Acceptance Criteria

1. THE System SHALL produce at least one automated test per acceptance criterion in this document, tagged with the criterion identifier (e.g., `DQC-3.3`), consistent with parent Requirement 21, AC1.
2. THE System SHALL cover the Inline_Goal_Capture atomicity rule (DQC-3, AC3) with a test asserting that an injected Habit_Track failure leaves zero Goal_Profiles persisted.
3. THE System SHALL cover Zero_State, Partial_State, and fully populated Dashboard renderings with Compose UI tests.
4. THE System SHALL cover the undo path (DQC-1, AC7) with a test asserting a deletion Change_Log event is emitted, not merely a local rollback.
5. THE System SHALL cover offline creation of each entity type with a test asserting local persistence within 200 ms and correct queueing of Change_Log events.
6. THE System SHALL cover every validation boundary named in this document — goal name length and uniqueness, keyword count and length, to-do text length, and the 100-item daily cap — with explicit boundary-value tests.
7. THE System SHALL cover DQC-6, AC2 with a Change_Log ordering test asserting goal events always precede dependent-entity events.
8. THE System SHALL fail the CI build on any failing test in these suites — no criterion in this document SHALL be marked complete on a red build (parent Requirement 21, AC13).

---

## Conflict Resolution Log

Points where this spec changes or clarifies the parent spec. Each requires a corresponding parent-document edit to keep the two consistent.

| # | Parent reference | Conflict | Resolution here | Parent edit needed |
|---|---|---|---|---|
| C-1 | Req 1, AC4 | Blocks Habit_Track and interception-rule creation at zero active goals, stranding any user who skipped onboarding. | The client presents Inline_Goal_Capture inside the dependent flow (DQC-3.2, DQC-5.4); the API-layer block remains fully in force (DQC-5.5). | Amend AC4 to state the block is an API-layer invariant and that clients SHALL offer inline goal creation rather than a terminal error. |
| C-2 | Req 17, AC4 | Assumes onboarding always completes and produces a goal; does not permit skipping. | Onboarding is skippable, landing on a functional Zero_State Dashboard (DQC-5.1–DQC-5.2). | Amend AC4 to permit early exit and require a functional Zero_State Dashboard. |
| C-3 | Design doc, `interception_rules.goal_id` | Column is nullable, implying goal-less rules, but the overlay content pipeline requires goal keywords. | v1 treats the goal association as mandatory (Assumption 2; see also `screen-time-interception-engine` STI-1, AC6). | Make `goal_id` `NOT NULL`, or document the nullable case's overlay behavior. |

---

## Backend Deltas

Additive only; none are breaking under parent Requirement 10, AC5.

1. **Atomic goal-with-dependent creation.** DQC-3, AC3 requires atomicity. A client-side rollback cannot be made atomic across a network boundary. **Recommendation:** add `POST /v1/goals` support for an optional nested `habitTrack` payload, committed in one server transaction.
2. **Single-call Dashboard state.** `GET /v1/dashboard/summary` SHALL include per-entity-type counts sufficient to distinguish Zero_State from Partial_State (DQC-5.3) in one response, so the Dashboard render path issues no more than one network call.
3. **No endpoint required** for bundled habit templates or category-to-keyword mappings in v1 (Assumption 3).

---

## Open Questions

| # | Question | Impact if unresolved | Proposed default |
|---|---|---|---|
| OQ-1 | When Inline_Goal_Capture runs inside the Intercept_Overlay (DQC-5.10), is full goal creation appropriate under a distraction-state UI? | Risk of an overloaded overlay contradicting the design system's deliberate airiness. | Collect name + category only in the overlay; prompt for keywords on the next Dashboard visit. |
| OQ-2 | Should habit templates be localized into `hi` and `mr`? | Template text is user-facing, but parent Req 12, AC5 excludes user-generated content from translation. | Templates are app-authored, not user-generated — translate them. Confirm with product. |
| OQ-3 | Should the undo window (DQC-1, AC6) also cover the inline goal created by Inline_Goal_Capture, or only the dependent entity? | Ambiguous undo could delete a goal the user intended to keep. | Undo reverts the whole transaction as created, with the confirmation text naming both entities. |

---

## Traceability Matrix

| This spec | Parent requirement(s) | Relationship |
|---|---|---|
| DQC-1 | 20 (AC4, AC14), 22 (AC7) | Extends — adds creation entry points to a dashboard previously specified as read-plus-quick-complete only |
| DQC-2 | 1 (AC1–AC2, AC7), 4 (AC4), 15 (AC4, AC6) | Refines — Android realization of goal creation |
| DQC-3 | 1 (AC4, AC6), 5 (AC1), 6 (AC1, AC5), 15 (AC10) | **Overrides** Req 1 AC4's client behavior (C-1); refines Req 6 |
| DQC-4 | 4 (AC1–AC3, AC12), 5 (AC1), 8 (AC4), 20 (AC14) | Refines |
| DQC-5 | 1 (AC4), 17 (AC4), 20 (AC11) | **Overrides** Req 17 AC4 (C-2) |
| DQC-6 | 4 (AC5, AC7, AC10), 18 (AC1–AC2, AC9) | Refines |
| DQC-7 | 21 (AC1, AC13) | Refines |
