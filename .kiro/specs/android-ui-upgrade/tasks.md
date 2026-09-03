# Implementation Plan: High-Fidelity Android UI Experience (DopaShift Kinetic)

## Overview

This plan implements the Android presentation layer for the seven DopaShift screens in the `ui` module (Compose + Material 3), following the multi-module Clean Architecture (`app → [domain, data, ui, sync, interception]`). Work proceeds bottom-up: first the **design-system layer** (tokens → `DopaShiftTheme`), then the reusable **Kinetic components**, then the **UI-state contracts**, then the **seven screen composables** and the **global bottom navigation**, then the **overlay** and **navigation wiring** in `app`, and finally the **test suites and CI gate**.

The central principle from the design is **presentation-only, no parallel data path**: screens render state produced by existing parent and sibling view-models and introduce no domain models, repositories, or endpoints. Property-based tests (Kotest, pure derivation helpers) validate the 8 correctness properties; Compose UI, instrumented, and accessibility tests cover the remaining criteria. All tests are tagged with their criterion id (AUI-9.1) and gate CI (AUI-9.10).

All code targets **Kotlin** (JVM 17), `compileSdk`/`targetSdk` 34, `minSdk` 26, per the tech stack.

## Tasks

- [ ] 1. Establish the design-system layer in the `ui` module
  - [ ] 1.1 Implement `DopaShiftTokens`
    - Map every color, text role, corner radius, spacing step, and touch-target value from `design_requirement_android.md` into a single Kotlin token object; no screen-local literals elsewhere
    - _Requirements: 8.1, 8.2_

  - [ ] 1.2 Implement `DopaShiftTypography` and `DopaShiftShapes`
    - Define Material 3 `Typography` with Hanken Grotesk (display/headline), Inter (body/label), and JetBrains Mono (metric/badge) roles; define `Shapes` (16dp cards, 12–14dp controls, full pills)
    - Register the three font families as bundled resources
    - _Requirements: 8.1, 8.4_

  - [ ] 1.3 Implement `DopaShiftTheme`
    - Wrap `MaterialTheme` with a dark `ColorScheme`, the typography, and the shapes; expose an accent-seed input so a Settings selection can re-tint the scheme app-wide
    - _Requirements: 8.1, 7.4_

  - [ ] 1.4 Write design-conformance tests
    - Assert cards render at 16dp radius with a 1dp `surfaceBorder` stroke and zero elevation; assert numeric readouts use the JetBrains Mono role
    - Tag `AUI-8.1`, `AUI-8.2`, `AUI-8.4`
    - _Requirements: 8.1, 8.2, 8.4_

- [ ] 2. Implement reusable Kinetic components
  - [ ] 2.1 Implement `CompletionRing`, `MomentumCheckbox`, and `KineticCard`
    - `CompletionRing`: integer-percent center in JetBrains Mono, teal arc on elevated track; `MomentumCheckbox`: 24dp box in a 48dp hit area, haptic + teal check + optional strikethrough on toggle; `KineticCard`: 16dp radius, 1dp stroke, zero elevation, optional accent stroke
    - Provide content descriptions for TalkBack on all three
    - _Requirements: 1.1, 1.3, 8.2, 8.3, 8.4, 8.5_

  - [ ] 2.2 Implement `SegmentedPill`, `KineticChip`, `UsageBar`, and `QuickCreateFab`
    - `SegmentedPill`: two-option Material 3 pill (selected = teal bg + dark text); `KineticChip`: rounded chip on `surfaceElevated`; `UsageBar`: horizontal bar with label + mono value + percent; `QuickCreateFab`: 56dp primary FAB delegating its click to the `dashboard-quick-create` surface
    - _Requirements: 3.3, 4.1, 6.5, 1.7, 4.3, 8.3_

  - [ ] 2.3 Write pure derivation helpers for state→render rules
    - Extract `continueEnabled(cooldown)`, `barColor(category)`, `roadmapFraction(completed)`, `formatPercent(score)`, and `completedRowStyle(done)` as pure functions so they are property-testable off-device
    - _Requirements: 2.7, 6.4, 5.5, 1.1, 1.5_

  - [ ] 2.4 Write property test for continue-button cooldown gating
    - **Property 1: Continue Button Gated Strictly by Cooldown**
    - **Validates: Requirements 2.7**
    - Kotest, 100+ iterations, tag `Feature: android-ui-upgrade, Property 1`; enabled iff `cooldownSeconds == 0` (satisfies AUI-9.4)

  - [ ] 2.5 Write property test for daily-bar color mapping
    - **Property 4: Daily Bar Color Matches Category**
    - **Validates: Requirements 6.4**
    - Kotest, 100+ iterations, tag `Feature: android-ui-upgrade, Property 4`; category → token color, no other mapping (satisfies AUI-9.7)

  - [ ] 2.6 Write property test for roadmap fraction and integer percent
    - **Property 3: Roadmap Progress Equals completed/30** and **Property 5: Efficiency Ring Renders an Integer Percentage**
    - **Validates: Requirements 5.5, 1.1, 8.4**
    - Kotest, 100+ iterations, tags `Feature: android-ui-upgrade, Property 3` and `Property 5` (satisfies AUI-9.6, AUI-9.2)

  - [ ] 2.7 Write property test for completed-item styling
    - **Property 6: Completed Items Are Struck Through and Dimmed**
    - **Validates: Requirements 1.5, 5.3**
    - Kotest, 100+ iterations, tag `Feature: android-ui-upgrade, Property 6`

  - [ ] 2.8 Write accessibility property test for touch targets
    - **Property 8: Every Interactive Control Meets the Touch Target**
    - **Validates: Requirements 8.3**
    - Assert every component's interactive hit area is ≥48dp × 48dp (satisfies AUI-9.9)

- [ ] 3. Checkpoint - Ensure design-system and component tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Define UI-state contracts (`ui` module, presentation view models)
  - [ ] 4.1 Add screen UI-state and row types
    - Add `DashboardUiState`, `OverlayUiState`, `AppLimitsUiState`, `GoalsUiState`, `GoalDetailUiState`, `AnalyticsUiState`, `SettingsUiState` and their row/item types (`TodoRowUi`, `GoalCardUi`, `RuleRowUi`, `DailyBarUi`, `UsageBarUi`, `AccentSwatch`, etc.)
    - Add enums `LimitType`, `DateRange`, `DailyBarCategory`, `TopDestination`; hold no persistence identity and reference existing domain models only for mapping
    - _Requirements: 1.1, 2.1, 3.3, 4.1, 5.5, 6.4, 7.3_

  - [ ] 4.2 Add domain→UI mappers
    - Map `Goal_Profile`, `DailyTodoItem`, `Habit_Track`, `Efficiency_Score`, and `Rule` into the UI-state types; derive `continueEnabled`, roadmap fraction, and bar category using the pure helpers from 2.3
    - _Requirements: 1.1, 2.7, 5.5, 6.4_

- [ ] 5. Implement the Home Dashboard screen (AUI-1)
  - [ ] 5.1 Implement `DashboardScreen`
    - Render header card (streak amber chip, greeting, sub-caption, 64dp efficiency `CompletionRing`), Primary Anchor `KineticCard` with teal accent stroke and `MomentumCheckbox`, Today's Focus card with inline quick-add and `TodoRowUi` rows, 2-column active-goals grid, `QuickCreateFab`, and the bottom nav with Home active
    - _Requirements: 1.1, 1.2, 1.4, 1.6, 1.7, 1.8_

  - [ ] 5.2 Write Compose UI and instrumented tests for the Dashboard
    - Compose UI: header ring integer percent + streak glyph, anchor card, focus list, goals grid, FAB, nav; instrumented: checkbox toggle fires haptic and ≤100ms strikethrough
    - Tag `AUI-1.1`, `AUI-1.2`, `AUI-1.3`, `AUI-1.4`, `AUI-1.6`, `AUI-1.7`, `AUI-1.8` (satisfies AUI-9.2, AUI-9.3)
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6, 1.7, 1.8_

- [ ] 6. Implement the Intercept Overlay screen (AUI-2)
  - [ ] 6.1 Implement `InterceptOverlayScreen`
    - Render 90% scrim backdrop, header (intercept pill, brain glyph, headline, app-named body), monospace cooldown badge, teal-bordered Behavioral Rescue Card with `MomentumCheckbox` + reward prompt, up to three pending tasks, and two stacked 52dp buttons; derive the secondary button's disabled state from `continueEnabled` supplied by `screen-time-interception-engine`; wire "Switch to DopaShift" to open the Dashboard and clear the overlay
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8_

  - [ ] 6.2 Write Compose UI tests for the overlay
    - Assert scrim, header content, cooldown badge, rescue card, pending tasks, button labels, disabled-until-zero gating, and switch action
    - Tag `AUI-2.1`, `AUI-2.2`, `AUI-2.3`, `AUI-2.4`, `AUI-2.5`, `AUI-2.6`, `AUI-2.7`, `AUI-2.8` (satisfies AUI-9.4)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8_

- [ ] 7. Implement the App Limits & Rules screen (AUI-3)
  - [ ] 7.1 Implement `AppLimitsScreen`
    - Render top search bar with clear control filtering installed apps in real time, high-contrast 48dp selection checkbox, `SegmentedPill` (`Once` / `Repetitive`), interval preset chips shown only in `Repetitive` with an explanatory tip, and an Active Rules list rendering `Rule` with Edit/Pause/Delete 48dp controls dispatching to the `screen-time-interception-engine` view-model
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

  - [ ] 7.2 Write Compose UI test for App Limits
    - Assert search filtering, selection checkbox, segmented toggle, preset visibility only in `Repetitive`, and rule rows
    - Tag `AUI-3.1`, `AUI-3.2`, `AUI-3.3`, `AUI-3.4`, `AUI-3.5` (satisfies AUI-9.5)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ] 8. Implement the Goals & Habits and Goal Detail screens (AUI-4, AUI-5)
  - [ ] 8.1 Implement `GoalsHabitsScreen`
    - Render goal summary cards (mini `CompletionRing`, title, category chip, subtitle counter, keyword chips), a bottom streak dot-matrix + roadmap status row, `QuickCreateFab`, and the bottom nav with Goals active
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

  - [ ] 8.2 Implement `GoalDetailScreen`
    - Render top app bar (back/edit/delete 48dp), category + keyword chips, interactive checklist (56dp rows, `MomentumCheckbox`, inline delete in coral), inline add-item field, and a Habit_Track progress card (`Day N/30`, `Active` badge, `completed/30` bar, sub-caption)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ] 8.3 Write Compose UI and instrumented tests for Goals and Detail
    - Compose UI: goal cards, streak row, FAB, nav, detail app bar, chips, checklist, roadmap bar (`completed/30`); instrumented: checklist checkbox strikethrough
    - Tag `AUI-4.1`, `AUI-4.2`, `AUI-4.3`, `AUI-4.4`, `AUI-5.1`, `AUI-5.2`, `AUI-5.3`, `AUI-5.4`, `AUI-5.5` (satisfies AUI-9.3, AUI-9.6)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 9. Implement the Analytics & Efficiency screen (AUI-6)
  - [ ] 9.1 Implement `AnalyticsScreen`
    - Render date-range `SegmentedPill` (7/30/90), an offline/partial-data warning banner in a coral container driven by `offlineOrPartial`, a 4-column summary metric grid, a daily-scores bar chart mapping category → token color with a legend, category `UsageBar`s, and the bottom nav with Analytics active
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ] 9.2 Write Compose UI test for Analytics
    - Assert range filter, offline banner visibility, metric grid, bar-chart colors + legend, usage bars, and nav
    - Tag `AUI-6.1`, `AUI-6.2`, `AUI-6.3`, `AUI-6.4`, `AUI-6.5`, `AUI-6.6` (satisfies AUI-9.7)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [ ] 10. Implement the Settings & Profile screen and Global Navigation (AUI-7)
  - [ ] 10.1 Implement `SettingsScreen`
    - Render profile section (64dp avatar, `Upload Photo` accepting JPEG/PNG ≤5MB, display-name input + Save), a "Manage App Limits" navigation card, a 12-swatch accent picker with a double-ring checkmark on the selection and a dynamic accent label, and a Keycloak-delegated password card
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ] 10.2 Implement `DopaShiftBottomNavBar`
    - Render exactly four destinations (Home, Goals, Analytics, Settings), highlight the active one with a teal pill + bold title, and host it across AUI-1/4/6/7
    - _Requirements: 7.6_

  - [ ] 10.3 Write Compose UI and instrumented tests for Settings and nav
    - Compose UI: profile section, manage-limits card, 12-swatch picker, Keycloak password card, four-destination nav; instrumented: swatch selection propagates accent across screens
    - Tag `AUI-7.1`, `AUI-7.2`, `AUI-7.3`, `AUI-7.4`, `AUI-7.5`, `AUI-7.6` (satisfies AUI-9.8)
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_

  - [ ] 10.4 Write property test for accent propagation
    - **Property 7: Accent Selection Propagates App-Wide**
    - **Validates: Requirements 7.4**
    - Kotest, 100+ iterations, tag `Feature: android-ui-upgrade, Property 7`

- [ ] 11. Wire navigation and overlay entry in the `app` module
  - [ ] 11.1 Wire the `NavHost` and bottom-nav destinations
    - Register Home, Goals, Analytics, Settings as `TopDestination` routes hosting the four screens; route Goal Detail and App Limits as pushed destinations; wire the `QuickCreateFab` to the `dashboard-quick-create` entry point
    - _Requirements: 1.7, 1.8, 4.3, 4.4, 6.6, 7.2, 7.6_

  - [ ] 11.2 Wire the overlay composable to the interception service
    - Host `InterceptOverlayScreen` in the `interception` module's overlay window, rendering the state it exposes (cooldown, rescue habit, pending tasks) with no parallel timer logic
    - _Requirements: 2.1, 2.7_

- [ ] 12. Localization and accessibility conformance
  - [ ] 12.1 Externalize all UI strings to `en`, `hi`, `mr` resources
    - Move every user-facing string in the seven screens and components into localized string resources; add `hi` and `mr` translations; fall back to `en` on a missing resource
    - _Requirements: 8.6_

  - [ ] 12.2 Add content descriptions and run the accessibility scan
    - Provide content descriptions for every non-text interactive control and informational icon; run the Accessibility Scanner and assert 100% touch-target and description conformance
    - Tag `AUI-8.3`, `AUI-8.5`, `AUI-8.6` (satisfies AUI-9.9)
    - _Requirements: 8.3, 8.5, 8.6_

- [ ] 13. Configure the CI gate for the spec test suites
  - [ ] 13.1 Register all suites in CI and enforce the gate
    - Ensure the property, Compose UI, instrumented, and accessibility suites run in CI; fail the build on any failing test so no criterion is marked complete on a red build; treat touch-target (AUI-9.9), cooldown gating (AUI-9.4), and bar-color mapping (AUI-9.7) tests as blocking
    - _Requirements: 9.1, 9.10_

- [ ] 14. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- This is a presentation-only spec: no domain models, repositories, Room entities, or endpoints are added. Screens render state from existing parent/sibling view-models.
- Each task references specific requirement acceptance criteria (AUI-N.M) for traceability.
- Property-based tests use Kotest with 100+ iterations against pure derivation helpers (extracted in task 2.3), tagged `Feature: android-ui-upgrade, Property {N}`.
- The 8 correctness properties each map to exactly one property-based test; the remaining criteria are validated by Compose UI, instrumented, and accessibility tests.
- The `domain` module stays untouched; all new code lives in `ui`, with navigation wiring in `app` and the overlay host in `interception`.
- Design tokens are sourced exclusively from `design_requirement_android.md` via `DopaShiftTokens`; screens never use raw literals.
- Checkpoints ensure incremental validation at layer boundaries.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "1.3"] },
    { "id": 2, "tasks": ["1.4", "2.1", "2.2"] },
    { "id": 3, "tasks": ["2.3"] },
    { "id": 4, "tasks": ["2.4", "2.5", "2.6", "2.7", "2.8", "4.1"] },
    { "id": 5, "tasks": ["4.2"] },
    { "id": 6, "tasks": ["5.1", "6.1", "7.1", "8.1", "8.2", "9.1", "10.1", "10.2"] },
    { "id": 7, "tasks": ["5.2", "6.2", "7.2", "8.3", "9.2", "10.3", "10.4"] },
    { "id": 8, "tasks": ["11.1", "11.2"] },
    { "id": 9, "tasks": ["12.1", "12.2"] },
    { "id": 10, "tasks": ["13.1"] }
  ]
}
```
