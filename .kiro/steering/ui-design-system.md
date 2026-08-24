---
inclusion: auto
---

# UI/UX Design System Rules

Quality bar: professional habit/goal-tracker apps (Habitify, Streaks, Fabulous) as interaction-pattern and polish references — not a visual clone. Never reproduce copyrighted visual assets or UI elements from those apps.

## Non-negotiable rules

- One consistent design system across the app: spacing scale, typography scale, color tokens, shared component library. No ad-hoc per-screen styling.
- Android: Material Design 3, native fit with Jetpack Compose.
- Web: a matching design-token system mirroring the same scale/tokens as Android, so the two platforms feel like one product.
- Light and dark theme support on both platforms, from the same token set (don't hardcode colors per theme separately).
- Progress visualization (streaks, completion rings/bars, charts) is a first-class UI element on habit/goal screens — not secondary text-only display.
- Onboarding is a guided, minimal-friction sequence (goal selection, first habit setup) — never a blank-state dashboard on first launch.

## When generating any new screen or component

Check the existing token set/component library first — extend it, don't introduce a new one-off style. If a screen needs a pattern not yet in the system, add it to the shared system, not just that screen.
