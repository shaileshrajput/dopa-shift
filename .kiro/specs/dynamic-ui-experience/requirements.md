# Requirements Document

# Dynamic & Professional Android UI Experience

**Spec ID:** `dynamic-ui-experience`
**Platform:** Android_App
**Version:** 1.0
**Date:** 2026-09-01
**Parent spec:** `.kiro/specs/dopa-shift/requirements.md`, `.kiro/specs/dopa-shift/design.md`
**Sibling specs:** `.kiro/specs/dashboard-quick-create/`, `.kiro/specs/screen-time-interception-engine/`
**Status:** Draft for review

---

## Introduction

The parent specification's Requirement 17 ("Professional UI/UX Design Standard") establishes a design system — the "DopaShift Kinetic" tokens — but stops short of specifying *how the interface behaves*: what adapts to screen size, what moves and when, what "professional" means in measurable terms, and what "catchy" means without becoming distracting in an app whose entire premise is fighting distraction. This document makes those behaviors explicit and testable, so a build can be judged pass/fail rather than by taste.

This is a child spec. It inherits every requirement of the parent `dopa-shift` spec unless it explicitly overrides one; overrides are recorded in the [Conflict Resolution Log](#conflict-resolution-log).

**A note on "catchy":** in a screen-time-interception product, an interface that maximizes engagement the way social apps do is a design failure, not a success — it would undercut the product's purpose. This spec interprets "catchy" as *satisfying and confidence-inspiring at the moment of use* (motion on task completion, a coherent visual system, legible data) rather than as attention-maximizing (autoplay, infinite scroll, variable-reward mechanics). Where this interpretation constrains a requirement below, it is called out explicitly.

---

## Scope

**In scope:** Adaptive layout across window size classes; the motion/animation system and its budgets; the visual design system's implementation rules (color, type, spacing, shape, theming); accessibility and touch ergonomics; performance budgets that gate whether "dynamic" UI remains usable.

**Out of scope:** The specific content of the Dashboard's creation flows (see `dashboard-quick-create`); the Intercept_Overlay's interception logic (see `screen-time-interception-engine`, though this spec governs that screen's visual and motion treatment); Web_Portal theming (parent Requirement 17 already covers it at the token level; no Android-specific override is needed there).

---

## Assumptions

1. **Design tokens are authoritative.** Colors, typography, spacing, and shape values come from `.kiro/specs/wireframes/dopashift_kinetic/DESIGN.md`. This spec references token names, never raw values, except where a numeric threshold is itself the requirement.
2. **Dark theme is the default** on first launch, per the design system's "Dark (Default)" note, with system-following available.
3. **Reference device class:** performance budgets in this document are measured against a mid-tier reference device (a device roughly matching the Android Vendor Test Suite's "mid-range" performance class, e.g., 4–6 GB RAM, released within the prior 3 years) — not a flagship.
4. **Minimum supported Android API level is 26**, consistent with `screen-time-interception-engine`.

---

## Glossary (additions to the parent glossary)

- **Window_Size_Class**: The Material 3 breakpoint classification — Compact (<600dp), Medium (600–839dp), Expanded (≥840dp).
- **Motion_Token**: A named duration + easing pair from the fixed motion scale defined in DUX-2.
- **Skeleton_State**: A non-interactive placeholder rendering of a screen section shown while that section's data is loading.
- **Zero_State / Partial_State**: As defined in the `dashboard-quick-create` spec — this document governs how those states are *rendered*, not when they occur.

---

## Wireframe References

| Screen | Wireframe Path | Requirements |
|---|---|---|
| Dashboard | `.kiro/specs/wireframes/dashboard/code.html` | DUX-1 |
| Intercept Overlay | `.kiro/specs/wireframes/intercept_overlay/code.html` | DUX-3, DUX-4 |
| Design System | `.kiro/specs/wireframes/dopashift_kinetic/DESIGN.md` | DUX-3, DUX-4, DUX-5 |
| Settings & LLM | `.kiro/specs/wireframes/settings/code.html` | DUX-4 |

---

## Requirements

### Requirement DUX-1: Adaptive Layout Across Window Size Classes

**User Story:** As a user, I want the app to make good use of my screen — whether it's a phone in one hand or a tablet on a stand — so that the interface never feels like a stretched or cramped afterthought.

#### Acceptance Criteria

1. THE Android_App SHALL classify its running window into Compact, Medium, or Expanded using Jetpack `WindowSizeClass` and SHALL re-evaluate this classification on every configuration change (rotation, multi-window resize, external display connect/disconnect).
2. THE Android_App SHALL lay out the Dashboard in a single column on Compact, two columns on Medium, and three-to-four columns on Expanded — consistent with the parent design system's "Modular Grid" (parent Requirement 20, AC13).
3. WHEN the window is resized or rotated THEN THE Android_App SHALL re-lay out affected screens without losing scroll position, in-progress form input, or any other transient UI state.
4. THE Android_App SHALL render a bottom navigation bar with exactly four destinations (Home, Goals, Analytics, Settings) on Compact, and SHALL replace it with a navigation rail carrying the same four destinations on Medium and Expanded (parent Requirement 17, AC8).
5. THE Android_App SHALL apply 24dp screen margins on Compact and SHALL increase margins proportionally on Medium and Expanded rather than merely widening content columns, so components never feel cramped against the screen edge (parent design system, "Margins").
6. THE Android_App SHALL support Android's multi-window and split-screen modes without clipping content, without an unusable minimum size, and without a crash, down to a window width of 320dp.
7. IF the device is foldable AND currently spanning a hinge, THEN THE Android_App SHALL avoid placing primary interactive controls under the hinge occlusion region, using Jetpack WindowManager's fold-state APIs where available.
8. THE Android_App SHALL apply the same adaptive rules defined in AC1–AC7 to the Goals, Analytics, and Settings screens, not to the Dashboard alone.

---

### Requirement DUX-2: Motion, Feedback, and Micro-Interaction Standard

**User Story:** As a user, I want completing a task to feel satisfying and the interface to feel responsive to my touch, so that daily use is pleasant rather than clerical — without the app resorting to the same attention-hooking tricks it exists to help me resist.

#### Acceptance Criteria

1. THE Android_App SHALL define a motion scale of exactly four Motion_Tokens and SHALL use only these for UI animation: `motion-instant` (100 ms), `motion-quick` (200 ms), `motion-standard` (300 ms), `motion-emphasized` (450 ms) — each paired with a defined Material 3 easing curve.
2. THE Android_App SHALL sustain 60 frames per second or the device's native refresh rate, whichever is lower, during scrolling and animation, with dropped frames not exceeding 1% of total frames measured over a standardized Dashboard-scroll benchmark.
3. WHEN the user completes a DailyTodoItem, Goal_Checklist_Item, or Habit_Track checkpoint THEN THE Android_App SHALL play a completion animation using `motion-quick` and SHALL trigger a light haptic confirmation — this is a single, bounded acknowledgment, not a recurring or escalating reward animation.
4. WHEN a Habit_Track streak increments to a multiple of 7 days THEN THE Android_App SHALL play a celebration animation of no more than 1500 milliseconds that does not block interaction and is dismissible by any touch — capped in both duration and frequency (at most once per streak milestone) so it never becomes a variable-reward mechanic.
5. THE Android_App SHALL animate progress rings and progress bars from their previous value to their new value using `motion-emphasized` rather than snapping to the new value.
6. WHEN a list renders for the first time in a session THEN THE Android_App SHALL apply a staggered entry animation with a per-item offset of 40 milliseconds, capped at 8 items — items beyond the cap SHALL appear without stagger, so long lists do not produce a long, distracting entrance sequence.
7. WHEN the user performs an action that changes persisted state THEN THE Android_App SHALL reflect the change optimistically in the UI before the write completes (parent Requirement 20, AC5).
8. IF an optimistic update is later rejected by validation or sync conflict resolution THEN THE Android_App SHALL revert the UI to the authoritative state and inform the user with a non-blocking message stating what changed.
9. IF the OS-level "Remove animations" / reduced-motion accessibility setting is enabled THEN THE Android_App SHALL disable all decorative animation, retain state changes and functionality unchanged, and SHALL NOT rely on animation alone to convey any information.
10. THE Android_App SHALL implement predictive back gesture support for all modal sheets and detail screens on Android 14 and higher.
11. THE Android_App SHALL provide haptic feedback for exactly these interaction classes and no others: completion confirmation, creation-menu expansion, destructive-action confirmation, and Intercept_Overlay appearance — haptics SHALL NOT be used for routine navigation or scrolling.
12. THE Android_App SHALL NOT display a blocking modal progress dialog for any operation whose expected duration is under 1 second; such operations SHALL use inline or optimistic feedback instead.
13. THE Android_App SHALL NOT implement autoplay-on-scroll video, infinite-scroll feeds without an end state, or any animation timed to re-engage the user after a period of inactivity — these patterns are explicitly excluded as contrary to the product's purpose.

---

### Requirement DUX-3: Dashboard and Screen Composition Dynamics

**User Story:** As a user, I want the home screen to reorganize itself around what actually matters today, so that it feels responsive to my life rather than a static form.

#### Acceptance Criteria

1. THE Android_App SHALL order Dashboard sections by a deterministic priority rule evaluated on each render: (a) an unchecked Habit_Track checkpoint for the current day, (b) overdue DailyTodoItems, (c) today's remaining DailyTodoItems, (d) the Active Goals grid, (e) the Efficiency trend, (f) the recent activity feed — a section with no content collapses to an inline prompt (per `dashboard-quick-create` DQC-5, AC3) and does not occupy a full section's layout slot.
2. THE priority rule in AC1 SHALL be deterministic and unit-testable: identical input state SHALL always produce identical section order.
3. WHEN Dashboard data is loading THEN THE Android_App SHALL render a Skeleton_State for each pending section within 100 milliseconds and SHALL NOT render a blank screen or a full-screen blocking spinner.
4. WHEN a Dashboard section's underlying data changes locally THEN THE Android_App SHALL update that section within 2 seconds via observable Kotlin Flow subscription, without polling (parent Requirement 14, AC4; Requirement 20, AC7).
5. THE Android_App SHALL render the daily Efficiency_Score as a circular progress indicator in the Dashboard header with the numeric percentage and a trend comparison, animating the ring from 0 to its value once per Dashboard session using `motion-emphasized` (parent Requirement 20, AC15).
6. IF the Efficiency_Score is unavailable for the current day THEN THE Android_App SHALL render a "No data" state inside the ring rather than rendering 0% (parent Requirement 7, AC2).
7. THE Android_App SHALL render each active Goal_Profile as a card showing goal name, category icon, streak count, progress ring with percentage, and a brief description (parent Requirement 20, AC13).
8. THE Android_App SHALL support pull-to-refresh on the Dashboard, triggering a Sync_Engine pull that updates all sections, with a refresh indicator shown for the operation's duration.
9. WHILE the device is offline THE Android_App SHALL render the full Dashboard from the local Room store with all quick-actions functional, and SHALL display a persistent, non-modal sync-pending indicator (parent Requirement 20, AC8; Requirement 4, AC10).
10. THE Android_App SHALL perform no network request on the Dashboard's synchronous render path — all Dashboard content SHALL render from the local store, with sync results applied reactively as they arrive.

---

### Requirement DUX-4: Visual Design and Theming Standard

**User Story:** As a user, I want the app to look like a professionally designed product, so that I trust it enough to grant it screen-time permissions and use it daily.

#### Acceptance Criteria

1. THE Android_App SHALL implement the "DopaShift Kinetic" design system tokens defined in `.kiro/specs/wireframes/dopashift_kinetic/DESIGN.md` — colors, typography, spacing, and shape SHALL be referenced by token name in code; hard-coded color, size, or spacing literals SHALL NOT appear in Composable UI code (parent Requirement 17, AC6).
2. THE Android_App SHALL apply the typography scale as specified: Hanken Grotesk for display/headline/title styles, Inter for body styles, JetBrains Mono for label and metadata styles.
3. THE Android_App SHALL apply an 8dp spacing rhythm for layout spacing, with 4dp permitted only for compact intra-component spacing.
4. THE Android_App SHALL apply the shape scale: 8dp radius for buttons, checkboxes, and inputs; 16dp for cards and progress containers; pill/full radius for chips; rounded stroke caps on all progress rings.
5. THE Android_App SHALL support light and dark themes with dark as the default, switchable to light or system-following, and every screen SHALL render legibly in both themes with no unreadable text and no invisible element (parent Requirement 17, AC2).
6. THE Android_App SHALL apply the user's selected accent color (parent Requirement 19, AC15) as the Material 3 `ColorScheme` seed, propagating it to buttons, toggles, progress indicators, and navigation highlights across all screens.
7. WHEN the user changes theme mode or accent color THEN THE Android_App SHALL apply the change to the currently visible screen without an app restart and without losing screen state.
8. THE Android_App SHALL render the Intercept_Overlay with a 20dp backdrop blur over a 95%-opacity `intercept-overlay` tint on devices supporting `RenderEffect` (API 31+), and SHALL fall back to a solid 97%-opacity scrim on devices below API 31 — the fallback SHALL NOT reduce the overlay's legibility or coverage (parent design system, "Elevation & Depth").
9. THE Android_App SHALL implement edge-to-edge display with correct system bar insets applied on every screen, including the Intercept_Overlay and every modal sheet.
10. THE Android_App SHALL NOT reproduce visual assets or copyrighted UI elements from Habitify, Streaks/Calistree, or Fabulous; those products are an interaction-quality reference only (parent Requirement 17, AC5).
11. THE Android_App SHALL define every user-facing string in `res/values/strings.xml` with translations in `values-hi` and `values-mr` — string literals SHALL NOT be hard-coded in Composable code (parent Requirement 12, AC2).
12. THE onboarding flow SHALL consist of no more than 5 screens, completable in under 2 minutes (parent Requirement 17, AC4), and SHALL apply the same token, motion, and accessibility rules defined in this document.

---

### Requirement DUX-5: Accessibility and Touch Ergonomics

**User Story:** As a user relying on assistive technology or a large font size, I want every screen to remain fully usable, so that the app is not effectively closed to me.

#### Acceptance Criteria

1. THE Android_App SHALL provide a minimum touch target of 48dp × 48dp for every interactive element, and a minimum row height of 56dp for task list items (parent design system, "Task List Items").
2. THE Android_App SHALL provide a content description for every non-decorative icon, image, and icon-only control, and SHALL mark decorative elements as such so screen readers skip them.
3. THE Android_App SHALL meet WCAG 2.1 AA contrast — 4.5:1 for body text and 3:1 for large text and meaningful non-text elements — in both light and dark themes and for every accent color selectable under parent Requirement 19, AC12.
4. IF a user-entered custom accent color would produce a contrast ratio below the AC3 thresholds against the active theme's surface THEN THE Android_App SHALL warn the user and offer the nearest compliant shade, while still allowing the user to proceed.
5. THE Android_App SHALL render all screens correctly at system font scales up to 200% without text truncation, overlap, or loss of any interactive control.
6. THE Android_App SHALL support full keyboard and D-pad navigation with a visible focus indicator on every focusable element.
7. THE Android_App SHALL NOT convey any state solely through color — completion, overdue, and missed states SHALL each carry a non-color indicator (icon, text, or type style) in addition to color.
8. THE Android_App SHALL announce dynamic content changes (a new to-do appearing, a progress ring updating) to screen readers via accessibility live regions where the change is user-initiated on the same screen.

---

### Requirement DUX-6: Performance Budgets Gating "Dynamic" UI

**User Story:** As a user, I want the richer, adaptive interface to still feel fast and light, so that visual polish never comes at the cost of responsiveness or battery life.

#### Acceptance Criteria

1. THE Android_App SHALL render the Dashboard's first meaningful frame within 1000 milliseconds of a cold start on the reference device (Assumption 3), using cached local data.
2. THE Android_App SHALL paginate the Dashboard activity feed in pages of 20 and analytics history in pages of no more than 50 records, loading further pages on scroll (parent Requirement 20, AC10; Requirement 16, AC4).
3. THE Android_App SHALL index local Room queries on `user_id`, `goal_id`, and `date` to avoid full-table scans on Dashboard and analytics rendering (parent Requirement 16, AC5).
4. THE Android_App SHALL keep the Dashboard's Compose recomposition count bounded such that a single quick-action (e.g., completing one to-do) triggers recomposition of only the affected section, not the full screen.
5. THE Android_App SHALL code-split heavy screens (Analytics charts, Settings) so their Composable and dependency code is not loaded into memory before first navigation to that screen.
6. THE motion system defined in DUX-2 SHALL NOT increase measured battery consumption attributable to UI rendering by more than 1% per hour of active foreground use, verified against the reference device.

---

### Requirement DUX-7: Verification of This Specification

**User Story:** As the system owner, I want each acceptance criterion here to be independently verifiable, so that "professional" and "dynamic" are engineering facts, not subjective sign-off.

#### Acceptance Criteria

1. THE System SHALL produce at least one automated test per acceptance criterion in this document, tagged with the criterion identifier (e.g., `DUX-4.1`), consistent with parent Requirement 21, AC1.
2. THE System SHALL cover DUX-1 (adaptive layout) with Compose UI tests at Compact, Medium, and Expanded Window_Size_Classes, plus a multi-window/split-screen test at the 320dp minimum width.
3. THE System SHALL verify DUX-4, AC1 with a lint rule that fails the build when a raw color, dimension, or string literal appears in Composable UI code.
4. THE System SHALL verify accessibility criteria (DUX-5) with automated checks for touch-target size, content descriptions, and contrast ratios, run in CI on every pull request.
5. THE System SHALL verify the performance budgets in DUX-6 with Macrobenchmark tests for cold start and Dashboard scroll jank, failing the build on regression beyond the stated thresholds.
6. THE System SHALL test the reduced-motion path (DUX-2, AC9) by asserting no animation duration exceeds `motion-instant` when the OS accessibility setting is enabled.
7. THE System SHALL cover the Dashboard section-ordering rule (DUX-3, AC1–AC2) with property-based tests asserting identical input state always yields identical output order.
8. THE System SHALL fail the CI build on any failing test in these suites — no criterion in this document SHALL be marked complete on a red build (parent Requirement 21, AC13).

---

## Conflict Resolution Log

This spec does not override any parent acceptance criterion; it fills gaps the parent leaves at implementation-time judgment. It is recorded here for completeness rather than because a behavior changes.

| # | Parent reference | Gap | Resolution here |
|---|---|---|---|
| G-1 | Req 17 (whole) | Establishes design tokens but specifies no motion system, no adaptive-layout rule, and no accessibility standard. | DUX-1, DUX-2, DUX-5 define these as testable requirements. |
| G-2 | Req 20, AC7 | States updates SHALL appear "within 2 seconds" but does not define what triggers a re-render or how sections reorder. | DUX-3, AC1–AC4 define the deterministic ordering and update mechanism. |
| G-3 | Req 16 (Performance) | Defines backend/service-level performance budgets but not UI-rendering budgets (frame rate, jank, cold start). | DUX-6 adds UI-specific budgets that compose with, rather than replace, parent Requirement 16. |

---

## Backend Deltas

None. This specification is entirely client-side; it introduces no new API surface or schema change.

---

## Open Questions

| # | Question | Impact if unresolved | Proposed default |
|---|---|---|---|
| OQ-1 | Should the 7-day streak celebration (DUX-2, AC4) be user-disable-able independent of the OS reduced-motion setting? | Some users may want functional accessibility motion off but still want celebratory motion, or vice versa. | Tie both to the OS reduced-motion setting only in v1; revisit if user feedback requests separate control. |
| OQ-2 | Does "catchy" as interpreted in this document (satisfying, not attention-maximizing) match stakeholder intent, or was more conventional gamification expected? | A mismatch here would require reworking DUX-2's exclusions (AC13) and possibly adding gamification requirements. | Confirm with product before implementation begins; this spec's interpretation is stated explicitly in the Introduction so it can be challenged early. |

---

## Traceability Matrix

| This spec | Parent requirement(s) | Relationship |
|---|---|---|
| DUX-1 | 17 (AC8), 20 (AC13) | Extends — parent has no adaptive-layout requirement |
| DUX-2 | 17 (AC1), 20 (AC5) | Extends — parent has no motion standard |
| DUX-3 | 7 (AC2), 14 (AC4), 20 (AC5, AC7–AC10, AC13, AC15) | Refines |
| DUX-4 | 12 (AC2), 17 (AC1–AC2, AC4–AC8), 19 (AC15) | Refines |
| DUX-5 | 17 (AC2), 19 (AC12) | Extends — parent has no accessibility requirement |
| DUX-6 | 16 (AC1, AC4–AC5) | Extends — adds UI-rendering-specific budgets |
| DUX-7 | 21 (AC1, AC13) | Refines |
