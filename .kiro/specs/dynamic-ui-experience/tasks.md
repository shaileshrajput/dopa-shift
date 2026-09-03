# Implementation Plan: Dynamic & Professional Android UI Experience

## Overview

This plan converts the `dynamic-ui-experience` design into incremental Kotlin/Jetpack Compose coding steps for the Android app. The feature is **client-side only** — there is no new backend API, schema, or Room table (Backend Deltas: None), so there are no backend tasks here.

Work lands mostly in the **`ui`** module, with pure, framework-free logic in **`domain`** (`DashboardSectionOrderer`, `MotionPolicy`, `ContrastCalculator`, and the stagger / streak-celebration / optimistic-revert / stagger-delay rules). Existing **`data`** module Flows and DataStore are reused (subscribed to, not extended). The Intercept overlay's visual/motion treatment touches **`interception` + `ui`**.

The build order is: pure `domain` logic first (fully unit/property-testable without an emulator) → theme + adaptive scaffold foundations → screen composition (Dashboard) → theming/accessibility wiring → interception overlay treatment → performance, static-analysis, and CI gates. Each step builds on the previous ones and ends by wiring the new code into a screen or gate so no code is left orphaned.

Testing follows the design's Testing Strategy: **Kotest property tests** (min 100 iterations, tagged `// Feature: dynamic-ui-experience, Property {n}` plus the DUX criterion) for the pure-logic subset (Properties 1–7, 13 in `domain`; 8–12 as Compose semantics scans in `ui`), **Compose UI tests** at Compact/Medium/Expanded + 320dp, **Jetpack Macrobenchmark** for performance budgets, and **lint/detekt** rules for literal/string/motion-token enforcement. Per DUX-7.1, every DUX acceptance criterion maps to at least one tagged automated test.

## Tasks

- [x] 1. Establish domain-layer presentation contracts and motion scale
  - [x] 1.1 Define pure domain types for Dashboard section ordering and motion
    - In the `domain` module create `DashboardSectionType` enum (HABIT_CHECKPOINT_DUE, OVERDUE_TODOS, TODAY_TODOS, ACTIVE_GOALS, EFFICIENCY_TREND, ACTIVITY_FEED), `DashboardSectionInput(type, itemCount)`, `OrderedSection(type, isCollapsedPrompt)`, and `MotionSpec(durationMillis, easingName)`
    - Keep all types framework-free (no Android/Compose imports) so they run under JVM tests
    - _Requirements: DUX-3.1, DUX-2.1_

- [x] 2. Implement pure Dashboard section-ordering logic
  - [x] 2.1 Implement `DashboardSectionOrderer.order`
    - In `domain`, return all six section types exactly once in the fixed priority order (a→f of DUX-3.1); mark sections with `itemCount == 0` as `isCollapsedPrompt = true`, others false
    - Function must be pure, total, and deterministic (identical input → identical output)
    - _Requirements: DUX-3.1, DUX-3.2_

  - [x] 2.2 Write property test for canonical section ordering
    - **Property 1: Canonical Dashboard section ordering**
    - **Validates: Requirements DUX-3.1, DUX-7.7**
    - Kotest property test in `domain`, min 100 iterations over shuffled section-input lists with arbitrary counts

  - [x] 2.3 Write property test for ordering determinism
    - **Property 2: Section ordering is deterministic**
    - **Validates: Requirements DUX-3.2, DUX-7.7**
    - Assert repeated invocation on identical input yields identical sequence and collapse flags

- [x] 3. Implement pure motion, stagger, and celebration decision logic
  - [x] 3.1 Implement `MotionPolicy.effective`
    - In `domain`, when `reducedMotion == true` collapse any `MotionSpec` duration to at most `motion-instant` (100 ms) / 0 while leaving the state change to the caller; otherwise return the base spec
    - _Requirements: DUX-2.9, DUX-7.6_

  - [x] 3.2 Write property test for reduced-motion collapse
    - **Property 3: Reduced motion collapses all decorative durations**
    - **Validates: Requirements DUX-2.9, DUX-7.6**
    - Generate over every `MotionTokens` spec; assert effective duration ≤ 100 ms under reduced motion

  - [x] 3.3 Implement staggered-entry delay function
    - In `domain`, compute per-item entry delay = `i × 40 ms` for `i < 8`, `0 ms` for `i ≥ 8`
    - _Requirements: DUX-2.6_

  - [x] 3.4 Write property test for capped stagger delay
    - **Property 4: Staggered entry delay follows the capped rule**
    - **Validates: Requirements DUX-2.6**
    - Generate arbitrary indices / list sizes; assert the capped rule holds

  - [x] 3.5 Implement streak-celebration decision logic
    - In `domain`, trigger a celebration for a new streak value iff it is a positive multiple of 7 not already celebrated in the sequence; expose the capped duration (≤ 1500 ms) and once-per-milestone guarantee
    - _Requirements: DUX-2.4_

  - [x] 3.6 Write property test for streak celebration firing
    - **Property 5: Streak celebration fires only on unseen 7-day multiples**
    - **Validates: Requirements DUX-2.4**
    - Generate arbitrary streak-value sequences; assert firing condition and ≤ 1500 ms duration

- [x] 4. Implement pure contrast-validation logic
  - [x] 4.1 Implement `ContrastCalculator`
    - In `domain`, implement `ratio(fg, bg)` (WCAG 2.1 relative contrast in [1.0, 21.0]), `meetsAA(fg, bg, largeText)` (4.5:1 body / 3:1 large & non-text), and `nearestCompliant(candidate, surface, largeText)`
    - _Requirements: DUX-5.3, DUX-5.4_

  - [x] 4.2 Write property test for contrast AA thresholds
    - **Property 12: Token color pairs meet WCAG AA in both themes and every accent**
    - **Validates: Requirements DUX-5.3**
    - Generate accent seeds + theme surfaces; assert token fg/bg pairs meet AA (4.5:1 / 3:1)

  - [x] 4.3 Write property test for nearest-compliant shade
    - **Property 13: Nearest-compliant shade always meets AA**
    - **Validates: Requirements DUX-5.4**
    - For candidates failing AA against a surface, assert `nearestCompliant` output meets AA

- [x] 5. Checkpoint - domain logic complete
  - Ensure all `domain` unit and property tests pass, ask the user if questions arise.

- [x] 6. Build the design-system theme foundation
  - [x] 6.1 Implement `DopaShiftTheme` with token-to-Compose mapping
    - In `ui`, build a Material 3 `ColorScheme` seeded from the accent (`accentSeed`), with `ThemeMode` DARK (default) | LIGHT | SYSTEM; map Hanken Grotesk / Inter / JetBrains Mono to display+headline+title / body / label `Typography` slots; map 8dp / 16dp / pill radii to `Shapes.small` / `Shapes.medium` / chip pill; expose spacing constants (8dp base, 4dp compact)
    - Reference tokens by name from `dopashift_kinetic/DESIGN.md`; no raw color/dimension literals in Composable code
    - Wire theme mode + accent from the existing `data` DataStore (`theme_mode`, `accent_seed`) via a `StateFlow` so recolor happens without restart
    - _Requirements: DUX-4.1, DUX-4.2, DUX-4.3, DUX-4.4, DUX-4.5, DUX-4.6, DUX-4.7_

  - [x] 6.2 Write property test for accent-seed propagation
    - **Property 7: Accent seed propagates to the color scheme**
    - **Validates: Requirements DUX-4.6**
    - Generate accent seeds; assert primary/accent-highlight roles derive from the seed

  - [x] 6.3 Write UI tests for typography/shape mapping and live theme switch
    - Snapshot/UI tests: typography+shape slot mapping (DUX-4.2–4.4), dark-default + legibility in both themes (DUX-4.5), live theme/accent change without restart or state loss (DUX-4.7)
    - _Requirements: DUX-4.2, DUX-4.3, DUX-4.4, DUX-4.5, DUX-4.7_

- [x] 7. Define motion tokens and reduced-motion bridge in the UI layer
  - [x] 7.1 Implement `MotionTokens` object and `rememberReducedMotion`
    - In `ui`, expose exactly four `MotionSpec` values (Instant 100ms/linear, Quick 200ms/fastOutSlowIn, Standard 300ms/fastOutSlowIn, Emphasized 450ms/emphasized) each paired with a Material 3 easing; implement `rememberReducedMotion()` reading `Settings.Global.ANIMATOR_DURATION_SCALE == 0`, feeding `MotionPolicy`
    - _Requirements: DUX-2.1, DUX-2.9_

  - [x] 7.2 Write UI test for reduced-motion path
    - Assert no animation duration exceeds `motion-instant` when the OS reduced-motion setting is enabled, while state changes remain
    - _Requirements: DUX-2.9, DUX-7.6_

- [x] 8. Build the adaptive layout scaffold
  - [x] 8.1 Implement `AdaptiveScaffold` and `TopDestination`
    - In `ui`, read `WindowSizeClass` (recomputed on configuration change): Compact → bottom nav bar (4 destinations) + 1-column + 24dp margins; Medium → navigation rail + 2-column + scaled margins; Expanded → navigation rail + 3–4-column + scaled margins; expose `content(columns, contentPadding)` with `LazyVerticalGrid`/`FlowRow` min-column-width so content reflows to 320dp without clipping
    - Observe `FoldingFeature` via `WindowInfoTracker`; reserve hinge occlusion bounds as padding so primary controls avoid the hinge
    - Preserve scroll position and in-progress input across reconfiguration via `rememberSaveable` + navigation-scoped ViewModels; apply edge-to-edge with system bar insets
    - `TopDestination` enum with exactly four values (HOME, GOALS, ANALYTICS, SETTINGS)
    - _Requirements: DUX-1.1, DUX-1.2, DUX-1.3, DUX-1.4, DUX-1.5, DUX-1.6, DUX-1.7, DUX-4.9_

  - [x] 8.2 Write Compose UI tests for adaptive layout across size classes
    - Parameterized suite at Compact / Medium / Expanded plus a multi-window/split-screen test at 320dp minimum width; assert nav affordance, column count, margins, and no clipping/crash
    - _Requirements: DUX-1.2, DUX-1.4, DUX-1.5, DUX-1.6, DUX-7.2_

  - [x] 8.3 Write UI tests for reconfiguration, fold, and insets
    - Config-change state retention (DUX-1.3), foldable hinge avoidance (DUX-1.7), edge-to-edge insets on every screen (DUX-4.9)
    - _Requirements: DUX-1.3, DUX-1.7, DUX-4.9_

- [x] 9. Checkpoint - theme and scaffold foundations complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement Dashboard state, skeletons, and reactive composition
  - [x] 10.1 Implement `DashboardViewModel` and `DashboardUiState`/`UiEvent`
    - In `ui`, define `DashboardUiState` (`Loading`, `Content(sections, efficiencyScore, isSyncPending, isRefreshing)`) and `UiEvent.Reverted(message)`; collect only local repository `Flow`s from the existing `data` module and call `DashboardSectionOrderer.order`; perform no network on the synchronous render path; apply later Flow emissions reactively so an affected section updates within 2s
    - Wire Hilt (KSP) injection for repositories/DataStore
    - _Requirements: DUX-3.1, DUX-3.4, DUX-3.10_

  - [x] 10.2 Implement `SectionSkeleton` and skeleton-first render
    - In `ui`, map `UiState.Loading` to a `Skeleton_State` per pending section sized to eventual content, rendered within 100 ms; never a blank screen or full-screen spinner
    - _Requirements: DUX-3.3_

  - [x] 10.3 Implement optimistic-update + revert transition
    - In the Dashboard/screen ViewModels, mutate in-memory `UiState` immediately on a persisted-state action, await the write/sync, and on rejection revert the field to the authoritative value and emit `UiEvent.Reverted` consumed as a non-blocking snackbar; keep pure decision logic in `domain`
    - _Requirements: DUX-2.7, DUX-2.8_

  - [x] 10.4 Write property test for optimistic revert
    - **Property 6: Rejected optimistic update reverts to the authoritative value**
    - **Validates: Requirements DUX-2.8**
    - Generate initial + optimistic field values; on rejection assert field equals original and `Reverted` emitted

  - [x] 10.5 Write UI tests for skeletons and offline/pull-to-refresh
    - Skeleton within 100 ms (DUX-3.3); full Dashboard from Room while offline with functional quick-actions and persistent non-modal sync-pending indicator (DUX-3.9); pull-to-refresh triggers sync pull with indicator (DUX-3.8)
    - _Requirements: DUX-3.3, DUX-3.8, DUX-3.9_

- [x] 11. Implement Dashboard visual components (efficiency ring, goal cards)
  - [x] 11.1 Implement `EfficiencyRing`
    - In `ui`, circular progress indicator in the header showing numeric percentage + trend comparison; animate 0→value once per session with `motion-emphasized`; render a "No data" state inside the ring when score is null (never 0%); progress rings use `motion-emphasized` for value transitions and rounded stroke caps
    - _Requirements: DUX-3.5, DUX-3.6, DUX-2.5_

  - [x] 11.2 Implement active Goal_Profile card
    - In `ui`, render each active goal as a card exposing goal name, category icon, streak count, progress ring with percentage, and brief description; use theme tokens and non-color state indicators
    - _Requirements: DUX-3.7_

  - [x] 11.3 Implement completion feedback and staggered list entry
    - In `ui`, on completing a DailyTodoItem / Goal_Checklist_Item / Habit_Track checkpoint play a single `motion-quick` completion animation + one light haptic; apply the capped 40ms staggered entry (from task 3.3) on first-in-session list render; implement the ≤1500ms non-blocking, touch-dismissible 7-day streak celebration (from task 3.5)
    - _Requirements: DUX-2.3, DUX-2.6, DUX-2.4_

  - [x] 11.4 Write Compose semantics-scan property tests for goal card and non-color state
    - **Property 8: Goal card renders all required fields** — **Validates: Requirements DUX-3.7**
    - **Property 11: State is never conveyed by color alone** — **Validates: Requirements DUX-5.7**
    - `ui` instrumented tests scanning semantics over generated Goal_Profile / state inputs

  - [x] 11.5 Write UI tests for efficiency ring and completion/haptics
    - Ring value/No-data/animate-once states (DUX-3.5, DUX-3.6, DUX-2.5); completion animation + haptic on the three completion classes (DUX-2.3); haptics restricted to the four permitted classes only (DUX-2.11)
    - _Requirements: DUX-3.5, DUX-3.6, DUX-2.3, DUX-2.5, DUX-2.11_

- [x] 12. Apply adaptive scaffold + theme to Goals, Analytics, Settings screens
  - [x] 12.1 Wire Goals, Analytics, and Settings into `AdaptiveScaffold` and `DopaShiftTheme`
    - In `ui`, host each screen in the shared scaffold so DUX-1 adaptive rules apply uniformly; apply accent seed to buttons, toggles, progress indicators, and navigation highlights across all screens; add predictive-back support for modal sheets and detail screens on Android 14+
    - _Requirements: DUX-1.8, DUX-4.6, DUX-2.10_

  - [x] 12.2 Implement Settings accent-color picker with contrast guard
    - In `ui`, on custom accent selection call `ContrastCalculator`; if below AA against the active surface, warn and offer the nearest compliant shade while still allowing the user to proceed
    - _Requirements: DUX-5.4, DUX-4.6_

  - [x] 12.3 Write UI tests for cross-screen adaptivity and predictive back
    - Run the parameterized adaptive suite across Goals/Analytics/Settings (DUX-1.8); predictive back on modal sheets/detail screens (DUX-2.10); accent propagation across screens (DUX-4.6)
    - _Requirements: DUX-1.8, DUX-2.10, DUX-4.6_

- [x] 13. Implement onboarding flow within the design system
  - [x] 13.1 Build onboarding flow (≤ 5 screens)
    - In `ui`, implement an onboarding flow of no more than 5 screens applying the same token, motion, and accessibility rules; keep it completable quickly with no blocking modal progress dialog for sub-second operations (inline/optimistic feedback instead)
    - _Requirements: DUX-4.12, DUX-2.12_

- [x] 14. Checkpoint - core screens complete
  - Ensure all tests pass, ask the user if questions arise.

- [x] 15. Implement Intercept_Overlay visual and motion treatment
  - [x] 15.1 Implement overlay blur/scrim rendering with API fallback
    - In `interception` + `ui`, keep overlay lifecycle in `interception`; compose its visuals in `ui` — 20dp `RenderEffect` backdrop blur over 95%-opacity `intercept-overlay` tint on API 31+, falling back to a solid 97%-opacity scrim below API 31 without reducing legibility or coverage; trigger the permitted overlay-appearance haptic; apply edge-to-edge insets
    - _Requirements: DUX-4.8, DUX-2.11, DUX-4.9_

  - [x] 15.2 Write UI tests for overlay blur/scrim branches
    - Assert API 31+ blur branch and pre-31 scrim fallback each render at full coverage/legibility
    - _Requirements: DUX-4.8_

- [x] 16. Implement accessibility affordances
  - [x] 16.1 Apply touch targets, content descriptions, and live regions
    - In `ui`, enforce 48dp×48dp minimum touch targets and 56dp task-row height; add content descriptions for non-decorative icons/images/icon-only controls and mark decorative elements; support keyboard/D-pad navigation with a visible focus indicator; render correctly at font scale up to 200% without truncation/overlap/control loss; announce user-initiated dynamic content changes (new to-do, ring update) via accessibility live regions
    - _Requirements: DUX-5.1, DUX-5.2, DUX-5.5, DUX-5.6, DUX-5.8_

  - [x] 16.2 Write Compose semantics-scan property tests for touch targets and descriptions
    - **Property 9: All interactive targets meet the minimum touch size** — **Validates: Requirements DUX-5.1**
    - **Property 10: Every non-decorative icon-only control has a content description** — **Validates: Requirements DUX-5.2**
    - `ui` instrumented tests scanning generated screen content

  - [x] 16.3 Write UI tests for font scaling and keyboard/D-pad focus
    - 200% font-scale robustness (DUX-5.5); keyboard/D-pad focus with visible indicator (DUX-5.6); live-region announcements (DUX-5.8)
    - _Requirements: DUX-5.5, DUX-5.6, DUX-5.8_

- [x] 17. Localize all user-facing strings
  - [x] 17.1 Externalize strings and provide hi/mr translations
    - Move every user-facing string to `res/values/strings.xml` with translations in `values-hi` and `values-mr`; no hard-coded string literals in Composable code
    - _Requirements: DUX-4.11_

  - [x] 17.2 Write resource-completeness test
    - Assert `values-hi` and `values-mr` contain every key present in `values/strings.xml`
    - _Requirements: DUX-4.11_

- [x] 18. Implement performance optimizations for the dynamic UI
  - [x] 18.1 Scope recomposition, paginate, index, and lazy-load
    - In `ui`/`data`, ensure a single quick-action recomposes only its affected section (stable keys, derived state); paginate the activity feed in pages of 20 and analytics history in pages ≤ 50, loading on scroll; declare Room `@Index` on `user_id`, `goal_id`, `date` on the existing queried entities (verify, no new tables); code-split heavy screens (Analytics charts, Settings) so their code isn't loaded before first navigation
    - _Requirements: DUX-6.2, DUX-6.3, DUX-6.4, DUX-6.5_

  - [x] 18.2 Write recomposition and lazy-loading tests
    - Recomposition-count test: one quick-action recomposes only its section (DUX-6.4); assert heavy-screen dependencies aren't initialized before first navigation (DUX-6.5)
    - _Requirements: DUX-6.4, DUX-6.5_

  - [x] 18.3 Write Room index assertion test
    - Confirm indexes on `user_id`, `goal_id`, `date` exist on the queried entities
    - _Requirements: DUX-6.3_

- [x] 19. Implement performance and power benchmarks
  - [x] 19.1 Write Macrobenchmark tests for cold start, scroll jank, and battery
    - Cold-start first-meaningful-frame < 1000 ms on the reference device using cached local data (DUX-6.1); Dashboard scroll: 60 fps / native refresh with < 1% dropped frames (DUX-2.2); motion battery overhead ≤ 1%/hour comparing motion-on vs motion-off foreground sessions (DUX-6.6)
    - _Requirements: DUX-6.1, DUX-2.2, DUX-6.6_

- [x] 20. Implement static-analysis and CI enforcement gates
  - [x] 20.1 Add lint/detekt rules for literals, strings, and motion tokens
    - Add rules failing the build on: raw color/dimension literals in Composables (DUX-4.1/DUX-7.3), hard-coded strings in Composables (DUX-4.11), and literal `tween`/duration values outside `MotionTokens` (DUX-2.1); include fixture tests confirming each rule fails on a violating sample
    - _Requirements: DUX-4.1, DUX-4.11, DUX-2.1, DUX-7.3_

  - [x] 20.2 Wire accessibility suite and coverage/exclusion checklist into CI
    - Run the accessibility suite (touch target, content description, contrast — Properties 9, 10, 12) on every pull request (DUX-7.4); add absence tests/checklist enforcing excluded patterns — no autoplay-on-scroll, no endless infinite-scroll, no re-engagement timers (DUX-2.13) — and competitor-asset avoidance (DUX-4.10); map every DUX criterion to its tagged test (DUX-7.1); configure CI to fail on any red test in these suites and gate perf regressions (DUX-7.5, DUX-7.8)
    - _Requirements: DUX-7.1, DUX-7.4, DUX-7.5, DUX-7.8, DUX-2.13, DUX-4.10_

- [x] 21. Final checkpoint - full verification
  - Ensure all property, UI, Macrobenchmark, lint, and CI-gate suites pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional test tasks and can be skipped for a faster MVP; core implementation tasks are never optional.
- Each task references the specific DUX acceptance criteria it implements for traceability; per DUX-7.1 every criterion maps to at least one tagged automated test.
- Property-based tests (min 100 iterations, Kotest) cover the pure-logic subset only — Properties 1–7 and 13 run as JVM tests in `domain`; Properties 8–12 run as Compose semantics scans in `ui`. Each is tagged `// Feature: dynamic-ui-experience, Property {n}: {property_text}` and references its DUX criterion.
- Adaptive layout, theming/rendering, and performance budgets are covered by Compose UI tests, snapshot tests, and Jetpack Macrobenchmark — not property tests — matching the design's Testing Strategy.
- This is a client-side Android feature: no backend API, schema, or new Room table is added; Room work is index verification on existing entities only.
- Checkpoints ensure incremental validation at natural breaks (domain logic, foundations, core screens, final verification).

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["2.1", "3.1", "3.3", "3.5", "4.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "3.2", "3.4", "3.6", "4.2", "4.3", "6.1", "7.1"] },
    { "id": 3, "tasks": ["6.2", "6.3", "7.2", "8.1"] },
    { "id": 4, "tasks": ["8.2", "8.3", "10.1", "10.2", "10.3"] },
    { "id": 5, "tasks": ["10.4", "10.5", "11.1", "11.2", "11.3", "15.1", "16.1", "17.1"] },
    { "id": 6, "tasks": ["11.4", "11.5", "12.1", "12.2", "15.2", "16.2", "16.3", "17.2", "18.1"] },
    { "id": 7, "tasks": ["12.3", "13.1", "18.2", "18.3", "20.1"] },
    { "id": 8, "tasks": ["19.1", "20.2"] }
  ]
}
```
