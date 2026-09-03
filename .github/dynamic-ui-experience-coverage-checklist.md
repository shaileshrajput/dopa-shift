# Dynamic UI Experience — CI Coverage & PBT-Exclusion Checklist

> Referenced by `.github/workflows/ci.yml` (Android job). This is the committed
> artifact for **DUX-7.1** (every acceptance criterion maps to at least one tagged
> automated test), the enforcement record for **DUX-2.13** / **DUX-4.10** (excluded
> patterns and competitor-asset avoidance), and the documentation of which
> correctness properties are covered by property-based tests vs. intentionally
> excluded and why.

The CI Android job (`android` in `ci.yml`) gates this feature by running:

| CI step | Gradle task(s) | Gates |
|---|---|---|
| Domain property tests | `:domain:test` | Properties 1–7, 13 (pure logic) |
| UI property + a11y + UI tests | `:ui:testDebugUnitTest` | Properties 8–12, accessibility suite, excluded-pattern absence test, resource completeness |
| Data module unit tests | `:data:testDebugUnitTest` | Room index assertion, reactive-update behaviour |
| Design-token governance gate | `:ui:verifyDesignTokens` | DUX-4.1 / DUX-2.1 / DUX-4.11 static analysis |

CI **fails on any red test** in these suites (DUX-7.8): no criterion is considered
complete on a red build. The accessibility suite (touch target, content
description, contrast — Properties 9, 10, 12) runs on every pull request (DUX-7.4).

---

## Accessibility suite (runs in CI on every PR — DUX-7.4)

| Check | Property | Test | Module task |
|---|---|---|---|
| Minimum touch target (48dp) & task-row height (56dp) | Property 9 (DUX-5.1) | `Property9And10TouchTargetAndDescriptionTest` | `:ui:testDebugUnitTest` |
| Content descriptions on non-decorative controls | Property 10 (DUX-5.2) | `Property9And10TouchTargetAndDescriptionTest` | `:ui:testDebugUnitTest` |
| WCAG AA contrast ratios | Property 12 (DUX-5.3) | `ContrastAaThresholdPropertyTest` | `:domain:test` |
| Nearest-compliant accent shade meets AA | Property 13 (DUX-5.4) | `NearestCompliantShadePropertyTest` | `:domain:test` |
| Font scale to 200% / keyboard & D-pad focus / live regions | — (DUX-5.5, 5.6, 5.8) | `FontScalingAndFocusUiTest` | `:ui:testDebugUnitTest` |
| State never conveyed by color alone | Property 11 (DUX-5.7) | `Property8And11GoalCardSemanticsTest` | `:ui:testDebugUnitTest` |

## Excluded-pattern & competitor-asset enforcement (DUX-2.13, DUX-4.10)

These criteria are prohibitions, so they are enforced as an **absence test** plus a
**review checklist**:

- **Automated:** `ExcludedPatternAbsenceTest` (`:ui:testDebugUnitTest`) scans the
  `ui` and `interception` module Kotlin source and fails the build if it finds
  autoplay-on-scroll, endless/infinite-scroll, or re-engagement/inactivity-timer
  constructs (DUX-2.13) or references to competitor assets — Habitify,
  Streaks/Calistree, Fabulous (DUX-4.10).
- **Review checklist (verify on every PR touching UI/feed/animation code):**
  - [ ] No video or media autoplays on scroll; playback is user-initiated.
  - [ ] Every scrollable feed (activity feed, analytics history) has a defined end
        state — it paginates to a terminal "no more items", never loops endlessly.
  - [ ] No animation, notification, or timer is scheduled to re-engage an inactive
        user (no "win-back" nudges timed to inactivity).
  - [ ] No visual asset, icon set, layout, or copy is reproduced from Habitify,
        Streaks/Calistree, or Fabulous — those remain interaction-quality
        references only.

---

## Full DUX criterion → test mapping (DUX-7.1)

Every acceptance criterion in `requirements.md` maps to at least one tagged
automated test. `PBT` = property-based test; `UI` = Compose UI/semantics test;
`Static` = lint/scan/resource check; `Bench` = Macrobenchmark (release pipeline).

### DUX-1 — Adaptive layout (UI tests; not property-tested — discrete per size class)

| Criterion | Type | Test |
|---|---|---|
| DUX-1.1–1.7 | UI | `AdaptiveScaffold` UI tests (Compact/Medium/Expanded, reconfig, fold, insets) |
| DUX-1.8 | UI | Cross-screen adaptive suite (Goals/Analytics/Settings) |

### DUX-2 — Motion & feel

| Criterion | Type | Test |
|---|---|---|
| DUX-2.1 | Static | `:ui:verifyDesignTokens` (literal `tween`/duration outside `MotionTokens`) |
| DUX-2.2 | Bench | Dashboard scroll jank Macrobenchmark (release pipeline) |
| DUX-2.3, 2.5, 2.11 | UI | Completion animation + haptics UI tests |
| DUX-2.4 | PBT | Property 5 — `StreakCelebrationFiringPropertyTest` |
| DUX-2.6 | PBT | Property 4 — `StaggerPolicyPropertyTest` |
| DUX-2.7, 2.8 | PBT | Property 6 — optimistic-revert transition test |
| DUX-2.9 | PBT + UI | Property 3 — `MotionPolicyReducedMotionPropertyTest` + reduced-motion UI test |
| DUX-2.10 | UI | Predictive-back UI tests |
| DUX-2.12 | UI | Onboarding no-blocking-dialog UI test |
| **DUX-2.13** | Static | `ExcludedPatternAbsenceTest` + review checklist above |

### DUX-3 — Dashboard composition

| Criterion | Type | Test |
|---|---|---|
| DUX-3.1 | PBT | Property 1 — `CanonicalSectionOrderingPropertyTest` |
| DUX-3.2 | PBT | Property 2 — `DashboardSectionOrdererDeterminismTest` |
| DUX-3.3 | UI | Skeleton-within-100ms UI test |
| DUX-3.4, 3.10 | UI | Reactive-update / no-network-on-render UI tests |
| DUX-3.5, 3.6 | UI | Efficiency-ring state UI tests |
| DUX-3.7 | PBT | Property 8 — `Property8And11GoalCardSemanticsTest` |
| DUX-3.8, 3.9 | UI | Pull-to-refresh / offline-render UI tests |

### DUX-4 — Visual design & theming

| Criterion | Type | Test |
|---|---|---|
| DUX-4.1 | Static | `:ui:verifyDesignTokens` (raw color literal scan) |
| DUX-4.2–4.5 | UI | Typography/shape/theme snapshot & UI tests |
| DUX-4.6 | PBT + UI | Property 7 — accent-seed propagation + accent UI tests |
| DUX-4.7 | UI | Live theme/accent-switch UI test |
| DUX-4.8 | UI | Overlay blur/scrim branch UI tests |
| DUX-4.9 | UI | Edge-to-edge insets UI tests |
| **DUX-4.10** | Static | `ExcludedPatternAbsenceTest` + review checklist above |
| DUX-4.11 | Static | `:ui:verifyDesignTokens` + `StringResourceCompletenessTest` |
| DUX-4.12 | UI | Onboarding-flow UI test |

### DUX-5 — Accessibility (see Accessibility suite table above)

| Criterion | Type | Test |
|---|---|---|
| DUX-5.1 | PBT | Property 9 |
| DUX-5.2 | PBT | Property 10 |
| DUX-5.3 | PBT | Property 12 |
| DUX-5.4 | PBT | Property 13 |
| DUX-5.5, 5.6, 5.8 | UI | `FontScalingAndFocusUiTest` |
| DUX-5.7 | PBT | Property 11 |

### DUX-6 — Performance budgets

| Criterion | Type | Test |
|---|---|---|
| DUX-6.1 | Bench | Cold-start Macrobenchmark (release pipeline, DUX-7.5) |
| DUX-6.2 | UI | Pagination UI tests |
| DUX-6.3 | Static | Room index assertion (`:data:testDebugUnitTest`) |
| DUX-6.4 | UI | Recomposition-count test |
| DUX-6.5 | UI | Lazy-load / heavy-screen dependency test |
| DUX-6.6 | Bench | Power Macrobenchmark (release pipeline) |

### DUX-7 — Verification meta

| Criterion | Type | Enforcement |
|---|---|---|
| DUX-7.1 | Static | This checklist (every criterion has a mapped test) |
| DUX-7.2 | UI | Multi-window/split-screen 320dp adaptive test |
| DUX-7.3 | Static | `:ui:verifyDesignTokens` |
| DUX-7.4 | CI | Accessibility suite runs on every PR (this job) |
| DUX-7.5 | Bench | Macrobenchmark regression thresholds (release pipeline) |
| DUX-7.6 | PBT + UI | Reduced-motion property + UI test |
| DUX-7.7 | PBT | Section-ordering properties 1 & 2 |
| DUX-7.8 | CI | Job fails on any red test in these suites |

---

## PBT coverage vs. exclusion rationale

**Property-tested (pure logic, Kotest, ≥100 iterations):** Properties 1–7 and 13
run as JVM tests in `:domain`; Properties 8–12 run as Compose semantics scans in
`:ui`. These are the parts of the feature whose correctness varies meaningfully
with input (section ordering, motion policy, stagger/celebration timing,
optimistic-revert, accent-seed propagation, touch-target/description/contrast
scans over generated content).

**Intentionally NOT property-tested, and why:**

- **Adaptive layout, nav affordances, margins (DUX-1):** discrete per size class —
  covered by parameterized Compose UI tests, not 100-iteration properties.
- **Theming config, typography/shape mapping, overlay branches (DUX-4.2–4.5,
  4.8–4.9):** configuration and rendering — snapshot/UI tests.
- **Frame rate, cold start, battery (DUX-2.2, DUX-6.1, DUX-6.6):** device
  performance measurement — Jetpack Macrobenchmark / power profiling (release
  pipeline), not runtime properties.
- **Codebase / resource constraints (DUX-2.1, DUX-4.1, DUX-4.10, DUX-4.11, DUX-6.3,
  DUX-6.5):** enforced by static analysis (`:ui:verifyDesignTokens`),
  resource-completeness checks, the excluded-pattern absence test, and review —
  these are source/resource facts, not runtime behaviours, so property-based
  quantification does not apply.
