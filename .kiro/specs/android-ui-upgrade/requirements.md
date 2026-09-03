# Requirements Document

## High-Fidelity Android UI Experience (DopaShift Kinetic)

**Spec ID:** `android-ui-upgrade`
**Platform:** Android_App (Jetpack Compose + Material 3)
**Version:** 1.0
**Date:** 2026-09-03
**Parent spec:** `.kiro/specs/dopa-shift/requirements.md`, `.kiro/specs/dopa-shift/design.md`
**Sibling specs:** `.kiro/specs/dynamic-ui-experience/`, `.kiro/specs/screen-time-interception-engine/`, `.kiro/specs/dashboard-quick-create/`
**Design tokens:** `.kiro/specs/android-ui-upgrade/design_requirement_android.md`
**Status:** Draft for review

---

## Introduction

This specification defines the visual architecture, behavioral-psychology patterns, and UI component structures for the seven primary screens of the DopaShift Android application. It formalizes the **DopaShift Kinetic** design language into verifiable EARS acceptance criteria suitable for automated ingestion by the Kiro agent platform.

Every requirement here is **client-side only**. This spec introduces zero backend schema changes, zero database-table modifications, and zero API alterations. All screens render existing parent-spec domain models (`Goal_Profile`, `DailyTodoItem`, `Habit_Track`, `Efficiency_Score`, `Rule`) using the design tokens defined in the referenced design-tokens file.

This is a **presentation-layer refinement** child spec. It inherits every requirement of the parent `dopa-shift` spec — most directly parent Requirement 17 (Professional UI/UX Design Standard) — and refines the Android realization of those screens without overriding parent behavior. Where a screen's data behavior is owned by a sibling spec (creation flows, interception engine, dynamic UI standards), this spec references that sibling rather than redefining it.

---

## Scope

**In scope:** The Android Compose presentation layer for seven screens — Home Dashboard, Intercept Overlay, App Limits & Rules, Goals & Habits Overview, Goal Detail & Roadmap, Analytics & Efficiency, and Settings & Profile; the shared design-token realization (colors, typography, spacing, shapes, elevation); the global bottom navigation bar; touch-target and accessibility conformance for these surfaces.

**Out of scope:** Creation flows and their data paths (owned by `dashboard-quick-create`); interception scheduling, cooldown, and rule persistence logic (owned by `screen-time-interception-engine`); cross-cutting motion, accessibility, and localization standards (owned by `dynamic-ui-experience`); all Backend endpoints, database schema, and API contracts; Web_Portal parity.

---

## Assumptions

1. **Minimum supported Android API level is 26**, `compileSdk`/`targetSdk` 34 (per tech stack).
2. **Jetpack Compose + Material 3** is the rendering path; the XML layout appendix (see Appendix A) is a reference/fallback artifact, not the primary implementation surface.
3. **Design tokens are authoritative.** Any literal color/typography/spacing value cited in this document is a human-readable mirror of the tokens in `design_requirement_android.md`; where the two differ, the token file wins.
4. **Data is supplied by existing use cases.** These screens consume state produced by the parent and sibling specs; they do not create a parallel data path.
5. **Dark theme is the primary theme** for v1 (DopaShift Kinetic is a dark-first system).

---

## Glossary

_Additions to the parent glossary._

- **DopaShift_Kinetic**: The Android design system defined by the referenced design-tokens file — deep-slate surfaces, Productive Blue and Momentum Teal accents, Hanken Grotesk / Inter / JetBrains Mono type roles.
- **Momentum_Teal**: The `#14B8A6` semantic accent reserved for dopamine-reward feedback — completion rings, habit completion, confirmed checkpoints, active-state pills.
- **Productive_Blue**: The `#2563EB` semantic accent reserved for primary actions, key navigation nodes, and focus structural highlights.
- **Completion_Ring**: A circular progress meter (SVG/Canvas) with a `#1A2436` track and a Momentum_Teal or Clarity-Cyan progression arc, centered on a JetBrains Mono numeric value.
- **Intercept_Overlay**: The full-screen intervention surface rendered over a blocked app when its allowance is depleted (data/scheduling behavior owned by `screen-time-interception-engine`).
- **Quick_Create_FAB**: The persistent floating action button opening the Quick-Create surface (flow owned by `dashboard-quick-create`); this spec specifies only its visual placement and appearance.
- **Touch_Target**: The minimum interactive hit area of 48dp × 48dp required for every actionable control.

---

## Design Tokens (human-readable mirror)

The authoritative token source is `design_requirement_android.md`. The values below are cited inline for readability of the acceptance criteria.

### Color palette (Dark Theme Kinetic)

| Token | Value | Role |
|---|---|---|
| `dopashift_background` | `#0B0F17` | Master backdrop |
| `dopashift_surface` | `#131B28` | Card surface base (16dp radius) |
| `dopashift_surface_elevated` | `#1A2436` | Inner containers, inputs, chips |
| `dopashift_surface_border` | `#1AFFFFFF` | 10% translucent white outline, 1dp |
| `dopashift_primary_blue` | `#2563EB` | Primary action / focus |
| `dopashift_momentum_teal` | `#14B8A6` | Reward feedback / habit completion |
| `dopashift_warning_amber` | `#F59E0B` | Streak fire & cooldown badges |
| `dopashift_danger_coral` | `#EF4444` | Distracting time, delete, pause |
| `dopashift_scrim_overlay` | `#E60B0F17` | 90% scrim for the Intercept Overlay |

### Typography roles

| Role | Family | Size |
|---|---|---|
| Display / Headline | Hanken Grotesk (`sans-serif-black`) | 24–28sp |
| Section title / card header | `sans-serif-bold` | 16–18sp |
| Body / input | Inter (`sans-serif`) | 14–15sp |
| Badge / timer / tag | JetBrains Mono (`monospace`) | 11–13sp |

### Touch targets & rhythm

| Element | Value |
|---|---|
| Minimum touch target | 48dp × 48dp |
| Task-list row min height | 56dp |
| Primary action button height | 52dp (14dp radius) |
| Card padding / outer margin | 16–20dp / 16dp |

---

## Screen & Wireframe References

| Screen | Requirement | Page render | Wireframe |
|---|---|---|---|
| Home Dashboard | AUI-1 | `pages/home_dashboard.png` | `.kiro/specs/wireframes/dashboard/` |
| Intercept Overlay | AUI-2 | `pages/intercept_overlay.png` | `.kiro/specs/wireframes/intercept_overlay/` |
| App Limits & Rules | AUI-3 | `pages/app_limits_rules.png` | `.kiro/specs/screen-time-interception-engine/` (sibling) |
| Goals & Habits Overview | AUI-4 | `pages/goals_habits.png` | `.kiro/specs/wireframes/goals_habits/` |
| Goal Detail & Roadmap | AUI-5 | `pages/goal_detail_roadmap.png` | `.kiro/specs/wireframes/habit_roadmap/` |
| Analytics & Efficiency | AUI-6 | `pages/analytics_efficiency.png` | `.kiro/specs/wireframes/analytics_audit/` |
| Settings & Profile | AUI-7 | `pages/settings_profile.png` | `.kiro/specs/wireframes/settings/` |
| Design tokens | AUI-8 | — | `.kiro/specs/wireframes/dopashift_kinetic/` |

---

## Requirements

### Requirement 1: [AUI-1] Home Dashboard

**User Story:** As a user opening DopaShift, I want to see my daily streak, efficiency score, today's primary habit anchor, today's focus to-do list, and active-goal progress cards immediately, so that I have complete cognitive clarity without clutter.

#### Acceptance Criteria

1. THE Android_App SHALL render a Dashboard header card containing: a streak pill badge with a flame glyph (e.g. `🔥 6 DAYS STREAK`) in `dopashift_warning_amber`; a greeting in 22sp `sans-serif-black`; a calming sub-caption in 13sp secondary text; and a 64dp × 64dp Completion_Ring displaying the daily `Efficiency_Score` percentage in `dopashift_momentum_teal`.
2. THE Android_App SHALL render a Primary Anchor Habit section immediately below the header showing the current day's `Habit_Track` micro-habit, with a 1.5dp `dopashift_momentum_teal` stroke, a 48dp Touch_Target checkbox, the habit title, a category/notes sub-line, and an estimated-time pill in JetBrains Mono.
3. WHEN the user taps the Primary Anchor Habit checkbox THEN THE Android_App SHALL trigger a haptic pulse, transition the check state within 100 milliseconds, and apply strikethrough styling in `dopashift_momentum_teal`.
4. THE Android_App SHALL render a "Today's Focus" card containing an inline quick-add input with placeholder text and an Add control (36dp height), task rows of at least 56dp height with 48dp checkbox hitboxes, and type-tag chips on a `dopashift_surface_elevated` background.
5. WHEN a `DailyTodoItem` is completed THEN THE Android_App SHALL render it at 50% opacity with strikethrough styling in the completed-text color (`#475569`).
6. THE Android_App SHALL render active goals as a 2-column card grid (or horizontally scrolling cards), each with a colored goal-icon container, a mini Completion_Ring with integer percentage, the goal category and title, and a pending-task counter.
7. THE Android_App SHALL render a persistent 56dp Quick_Create_FAB anchored bottom-right in `dopashift_primary_blue` with a white plus glyph; activating it opens the Quick-Create surface owned by `dashboard-quick-create`.
8. THE Android_App SHALL render the Dashboard as a member of the Global Bottom Navigation (see AUI-7, AC5) with "Home" as the active destination.

---

### Requirement 2: [AUI-2] Intercept Overlay

**User Story:** As a distracted user opening a restricted app whose allowance has expired, I want a firm yet calm full-screen intervention presenting an immediate micro-habit rescue and pending tasks before allowing me to proceed, so that I redirect compulsive screen time into productive momentum.

_Scheduling, cooldown duration, and rule evaluation are owned by `screen-time-interception-engine`; this requirement specifies the overlay's visual and interaction surface only._

#### Acceptance Criteria

1. THE Android_App SHALL render the Intercept_Overlay over the blocked application using a 90% deep-slate scrim (`dopashift_scrim_overlay`) or a native blur.
2. THE Android_App SHALL render an overlay header containing a `• DOPAMINE INTERCEPT` pill in `dopashift_momentum_teal`, a centered 56dp brain glyph in `dopashift_primary_blue`, a 24sp bold headline, and body copy naming the specific target app.
3. THE Android_App SHALL render a monospace cooldown badge (e.g. `⏳ [N]s cooldown until dismiss`) on a `dopashift_surface_elevated` background, counting down every second from the configured minimum duration supplied by `screen-time-interception-engine`.
4. THE Android_App SHALL render a Behavioral Rescue Card bordered in `dopashift_momentum_teal` containing a `Quick Micro-Habit (Day N/30)` label, a 48dp Touch_Target checkbox with a habit description, and a reward-prompt line in `dopashift_momentum_teal`.
5. THE Android_App SHALL render up to three pending focus tasks, each with an estimated-time badge in JetBrains Mono.
6. THE Android_App SHALL render two stacked 52dp action buttons: a primary `↗ Switch to DopaShift` button in `dopashift_primary_blue`, and a secondary `⊘ Continue to app ([N]s)` button in `dopashift_surface`.
7. WHILE the cooldown counter is greater than zero THE Android_App SHALL keep the secondary "Continue to app" button disabled, and WHEN the counter reaches zero THE Android_App SHALL enable it.
8. WHEN the user activates "Switch to DopaShift" THEN THE Android_App SHALL open the Dashboard and clear the overlay.

---

### Requirement 3: [AUI-3] App Limits & Rules Authoring

**User Story:** As a user managing digital boundaries, I want to search installed apps, toggle between a daily limit and a repetitive interval, choose preset interval times, and manage active rules, so that I control how often I am intercepted.

_Rule persistence and repetitive-interval evaluation semantics are owned by `screen-time-interception-engine`; this requirement specifies the authoring UI surface only._

#### Acceptance Criteria

1. THE Android_App SHALL render a top search bar with a search glyph and a clear (`X`) control that filters installed apps in real time as the user types.
2. THE Android_App SHALL render a selected-app indicator using a high-contrast checkbox meeting the 48dp Touch_Target.
3. THE Android_App SHALL render a Material 3 segmented pill selector with exactly two options — `Once (Daily Limit)` and `Repetitive (Interval)` — where the selected option uses a `dopashift_momentum_teal` background with dark text and the unselected option uses `dopashift_surface`.
4. WHEN `Repetitive (Interval)` is selected THEN THE Android_App SHALL render interval preset chips — `15 min`, `30 min`, `45 min`, `60 min`, and `Custom...` — and an informative tip explaining the repetitive-interception rule.
5. THE Android_App SHALL render an Active Rules list where each row shows the app icon, app label and package name, a rule-type indicator (`Daily limit: 30m` or `Repetitive: Every 30m • 3 loops`), and `Edit`, `Pause today`, and `Delete` controls each meeting the 48dp Touch_Target.

---

### Requirement 4: [AUI-4] Goals & Habits Overview

**User Story:** As a user tracking multiple life domains, I want to see all my active goals with progress rings, category badges, keyword chips, and habit streak counters, so that I can monitor holistic progress.

#### Acceptance Criteria

1. THE Android_App SHALL render each `Goal_Profile` as a summary card containing a Completion_Ring with integer percentage, a bold 18sp title, a category pill badge on `dopashift_surface_elevated` with a teal accent, a subtitle counter (e.g. `1/2 tasks pending`), and keyword chips on `dopashift_surface_elevated`.
2. THE Android_App SHALL render, on each goal card, a bottom row with dot-matrix streak indicators (completed days in `dopashift_momentum_teal`, remaining days in gray) and a roadmap status line.
3. THE Android_App SHALL render a persistent 56dp Quick_Create_FAB anchored bottom-right that opens the Quick-Create surface owned by `dashboard-quick-create`.
4. THE Android_App SHALL render this screen as a member of the Global Bottom Navigation with "Goals" as the active destination.

---

### Requirement 5: [AUI-5] Goal Detail & 30-Day Roadmap

**User Story:** As a user working on a specific goal, I want to inspect and edit its keywords, manage its task checklist, and track its 30-day habit-roadmap progress, so that I maintain clear execution steps.

#### Acceptance Criteria

1. THE Android_App SHALL render a top app bar with a back arrow, the goal name, an edit control, and a delete control, each meeting the 48dp Touch_Target.
2. THE Android_App SHALL render the goal category and keyword chips beneath the title on a `dopashift_surface_elevated` background.
3. THE Android_App SHALL render an interactive task checklist with 56dp row heights and 48dp Touch_Target checkboxes; completed items SHALL show a checked `dopashift_momentum_teal` box with strikethrough text, and each item SHALL have an inline delete control in `dopashift_danger_coral`.
4. THE Android_App SHALL render an inline "Add checklist item" text field with a plus control that captures a checklist item without leaving the screen.
5. THE Android_App SHALL render a Habit_Track progress card showing a current-day counter (`Day N/30`) with an `Active` status badge, a horizontal progress bar computed as `completed / 30`, and a `M of 30 days completed` sub-caption.

---

### Requirement 6: [AUI-6] Analytics & Efficiency Auditing

**User Story:** As a user auditing my focus habits, I want to view historical daily efficiency scores, productive-vs-distracting time charts, and category time breakdowns across 7, 30, and 90-day intervals, so that I can measure my progress over time.

#### Acceptance Criteria

1. THE Android_App SHALL render a segmented date-range filter with `Last 7 days`, `Last 30 days`, and `Last 90 days`, where the active option uses a `dopashift_momentum_teal` background with high-contrast text.
2. WHEN the app is offline OR the selected range contains partial data THEN THE Android_App SHALL render a warning banner in a `dopashift_danger_coral` container explaining that some data is unavailable offline and will resume on reconnect.
3. THE Android_App SHALL render a 4-column summary metric grid showing `Average`, `Highest`, `Lowest`, and `Days tracked` values derived from `Efficiency_Score` history.
4. THE Android_App SHALL render a daily-scores bar chart where productive days use `dopashift_momentum_teal`, distracting days exceeding limits use `dopashift_danger_coral`, and inactive/no-data days use subtle gray (`#1E293B`), with a bottom legend for each category.
5. THE Android_App SHALL render horizontal usage progress bars for top categories, each showing a label, absolute time, and percentage in its category color.
6. THE Android_App SHALL render this screen as a member of the Global Bottom Navigation with "Analytics" as the active destination.

---

### Requirement 7: [AUI-7] Settings, Profile & Global Navigation

**User Story:** As a user configuring DopaShift, I want to manage my profile avatar, edit my display name, navigate to app-limit rules, customize my visual theme accent color, and update security credentials, so that the app matches my workflow.

_Password management is delegated to Keycloak per parent Requirement 22; this requirement specifies the UI surface only._

#### Acceptance Criteria

1. THE Android_App SHALL render a profile section with a 64dp circular avatar (photo or default monogram), an `Upload Photo` control accepting JPEG/PNG up to 5MB, and a display-name input with a Save control.
2. THE Android_App SHALL render a prominent clickable "Manage App Limits" card with an explanatory subtitle and a chevron navigating to the App Limits & Rules screen (AUI-3).
3. THE Android_App SHALL render an accent-theme palette of 12 circular color swatches, where the selected swatch shows a checkmark inside a double-ring indicator and a dynamic accent label names the active theme.
4. WHEN the user selects an accent swatch THEN THE Android_App SHALL apply the chosen accent seed across all screens.
5. THE Android_App SHALL render a security card stating that password change is managed through Keycloak, with Current, New, and Confirm password inputs that delegate to the Keycloak flow.
6. THE Android_App SHALL render a persistent Global Bottom Navigation across the Home (AUI-1), Goals (AUI-4), Analytics (AUI-6), and Settings (AUI-7) screens with exactly four destinations — `Home`, `Goals`, `Analytics`, `Settings` — and SHALL highlight the active destination with a `dopashift_momentum_teal` pill container and a bold title.

---

### Requirement 8: [AUI-8] Design-System Conformance & Accessibility

**User Story:** As the product owner, I want every DopaShift Android screen to render the DopaShift Kinetic tokens consistently and meet accessibility minimums, so that the product feels credible and is usable by everyone.

#### Acceptance Criteria

1. THE Android_App SHALL source every color, type role, corner radius, and spacing step from the design tokens defined in `design_requirement_android.md` rather than screen-local literals.
2. THE Android_App SHALL render all card surfaces at 16dp corner radius with a 1dp `dopashift_surface_border` stroke and zero elevation shadow, using tonal layering for depth.
3. THE Android_App SHALL ensure every interactive control meets the 48dp × 48dp Touch_Target minimum.
4. THE Android_App SHALL render all real-time numeric readouts (timers, counters, metrics) in JetBrains Mono to eliminate layout jitter on update.
5. THE Android_App SHALL provide a content description for every non-text interactive control and every informational icon, sufficient for TalkBack navigation.
6. THE Android_App SHALL render all user-facing UI strings through the localized string resources for `en`, `hi`, and `mr` (parent Requirement 12), and SHALL NOT hardcode user-facing copy in composables.

---

### Requirement 9: [AUI-9] Verification of This Specification

**User Story:** As the system owner, I want each acceptance criterion here to be independently verifiable, so that implementation cannot report completion without evidence.

#### Acceptance Criteria

1. THE System SHALL produce at least one automated test per acceptance criterion in this document, tagged with the criterion identifier (e.g. `AUI-1.3`), consistent with parent Requirement 21, AC1.
2. THE System SHALL verify the Efficiency_Score Completion_Ring and streak rendering (AUI-1.1) with a Compose UI test asserting the integer percentage and streak glyph.
3. THE System SHALL verify checkbox completion behavior (AUI-1.3, AUI-5.3) with instrumented tests asserting strikethrough transition and haptic invocation.
4. THE System SHALL verify the cooldown gating of the "Continue to app" button (AUI-2.7) with a test asserting the button is disabled until the counter reaches zero.
5. THE System SHALL verify the segmented limit-type toggle (AUI-3.3, AUI-3.4) with a semantics test asserting the interval picker appears only in `Repetitive` mode.
6. THE System SHALL verify the roadmap progress computation (AUI-5.5) with a test asserting the bar reflects `completed / 30`.
7. THE System SHALL verify the daily-scores chart color thresholds (AUI-6.4) with a semantics test asserting bar colors match each daily score category.
8. THE System SHALL verify the 12-swatch accent picker (AUI-7.3, AUI-7.4) with a Compose test asserting swatch selection updates the accent seed across screens.
9. THE System SHALL verify Touch_Target conformance (AUI-8.3) with an accessibility check asserting 100% of interactive targets meet 48dp × 48dp.
10. THE System SHALL fail the CI build on any failing test in these suites — no criterion in this document SHALL be marked complete on a red build (parent Requirement 21, AC13).

---

## Traceability Matrix

| This spec | Parent requirement(s) | Sibling spec(s) | Relationship |
|---|---|---|---|
| AUI-1 Home Dashboard | 17, 20, 7 | `dashboard-quick-create` (FAB/quick-add flow) | Refines — Android render of the dashboard surface |
| AUI-2 Intercept Overlay | 17, 2, 6 | `screen-time-interception-engine` (scheduling/cooldown) | Refines — overlay presentation only |
| AUI-3 App Limits & Rules | 17, 2 | `screen-time-interception-engine` (rule semantics) | Refines — authoring UI only |
| AUI-4 Goals & Habits | 17, 1, 6 | — | Refines |
| AUI-5 Goal Detail & Roadmap | 17, 1, 6 | — | Refines |
| AUI-6 Analytics & Efficiency | 17, 7, 8 | — | Refines |
| AUI-7 Settings & Profile | 17, 19, 22 | `screen-time-interception-engine` (limits target) | Refines |
| AUI-8 Design conformance | 17, 12 | `dynamic-ui-experience` (motion/a11y standards) | Refines |
| AUI-9 Verification | 21 | — | Refines |

---

## Appendix A: Reference Android XML Layout Tokens

_Non-normative. The primary implementation surface is Jetpack Compose + Material 3 (Assumption 2). The following resource files are provided as a reference realization of the design tokens for teams maintaining any XML-based views._

```xml
<!-- res/values/colors.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Dark Theme Kinetic Surfaces -->
    <color name="dopashift_background">#0B0F17</color>
    <color name="dopashift_surface">#131B28</color>
    <color name="dopashift_surface_elevated">#1A2436</color>
    <color name="dopashift_surface_border">#1AFFFFFF</color>

    <!-- Accents & Functional Colors -->
    <color name="dopashift_primary_blue">#2563EB</color>
    <color name="dopashift_primary_blue_variant">#1D4ED8</color>
    <color name="dopashift_momentum_teal">#14B8A6</color>
    <color name="dopashift_momentum_teal_container">#1A14B8A6</color>
    <color name="dopashift_focus_indigo">#6366F1</color>

    <!-- Text Tiers -->
    <color name="dopashift_text_primary">#F8FAFC</color>
    <color name="dopashift_text_secondary">#94A3B8</color>
    <color name="dopashift_text_tertiary">#64748B</color>
    <color name="dopashift_text_completed">#475569</color>

    <!-- Feedback & Alerts -->
    <color name="dopashift_warning_amber">#F59E0B</color>
    <color name="dopashift_danger_coral">#EF4444</color>
    <color name="dopashift_scrim_overlay">#E60B0F17</color>
</resources>
```

```xml
<!-- res/values/styles.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.DopaShift" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="android:windowBackground">@color/dopashift_background</item>
        <item name="android:statusBarColor">@color/dopashift_background</item>
        <item name="android:navigationBarColor">@color/dopashift_background</item>
        <item name="colorPrimary">@color/dopashift_primary_blue</item>
        <item name="colorSecondary">@color/dopashift_momentum_teal</item>
        <item name="colorSurface">@color/dopashift_surface</item>
    </style>

    <style name="Widget.DopaShift.Card" parent="Widget.Material3.CardView.Filled">
        <item name="cardBackgroundColor">@color/dopashift_surface</item>
        <item name="cardCornerRadius">16dp</item>
        <item name="cardElevation">0dp</item>
        <item name="strokeColor">@color/dopashift_surface_border</item>
        <item name="strokeWidth">1dp</item>
    </style>

    <style name="Widget.DopaShift.Button.Primary" parent="Widget.Material3.Button">
        <item name="android:layout_height">52dp</item>
        <item name="backgroundTint">@color/dopashift_primary_blue</item>
        <item name="cornerRadius">14dp</item>
        <item name="android:fontFamily">sans-serif-medium</item>
        <item name="android:textSize">15sp</item>
        <item name="android:textAllCaps">false</item>
    </style>
</resources>
```
