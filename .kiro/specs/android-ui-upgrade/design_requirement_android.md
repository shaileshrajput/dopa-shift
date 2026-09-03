---
name: DopaShift Kinetic
colors:
  surface: '#0f131c'
  surface-dim: '#0f131c'
  surface-bright: '#353942'
  surface-container-lowest: '#0a0e16'
  surface-container-low: '#181c24'
  surface-container: '#1c2028'
  surface-container-high: '#262a33'
  surface-container-highest: '#31353e'
  on-surface: '#dfe2ee'
  on-surface-variant: '#c3c6d7'
  inverse-surface: '#dfe2ee'
  inverse-on-surface: '#2c3039'
  outline: '#8d90a0'
  outline-variant: '#434655'
  surface-tint: '#b4c5ff'
  primary: '#b4c5ff'
  on-primary: '#002a78'
  primary-container: '#2563eb'
  on-primary-container: '#eeefff'
  inverse-primary: '#0053db'
  secondary: '#4fdbc8'
  on-secondary: '#003731'
  secondary-container: '#04b4a2'
  on-secondary-container: '#003f38'
  tertiary: '#7bd0ff'
  on-tertiary: '#00354a'
  tertiary-container: '#00759f'
  on-tertiary-container: '#e1f2ff'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#dbe1ff'
  primary-fixed-dim: '#b4c5ff'
  on-primary-fixed: '#00174b'
  on-primary-fixed-variant: '#003ea8'
  secondary-fixed: '#71f8e4'
  secondary-fixed-dim: '#4fdbc8'
  on-secondary-fixed: '#00201c'
  on-secondary-fixed-variant: '#005048'
  tertiary-fixed: '#c4e7ff'
  tertiary-fixed-dim: '#7bd0ff'
  on-tertiary-fixed: '#001e2c'
  on-tertiary-fixed-variant: '#004c69'
  background: '#0f131c'
  on-background: '#dfe2ee'
  surface-variant: '#31353e'
typography:
  display-lg:
    fontFamily: Hanken Grotesk
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Hanken Grotesk
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 22px
    fontWeight: '600'
    lineHeight: 28px
  title-md:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
  metric-display:
    fontFamily: JetBrains Mono
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.04em
  metric-counter:
    fontFamily: JetBrains Mono
    fontSize: 18px
    fontWeight: '500'
    lineHeight: 24px
    letterSpacing: -0.02em
  label-mono:
    fontFamily: JetBrains Mono
    fontSize: 11px
    fontWeight: '400'
    lineHeight: 16px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  spacing-4: 0.25rem
  spacing-8: 0.5rem
  spacing-12: 0.75rem
  spacing-16: 1rem
  spacing-20: 1.25rem
  spacing-24: 1.5rem
  spacing-32: 2rem
  spacing-48: 3rem
  margin-mobile: 1rem
  margin-tablet: 1.5rem
  gutter-grid: 1rem
  touch-target-min: 3rem
---

## Brand & Style

The design system embodies a modern, clinical-grade behavioral health and productivity environment tailored for high-focus Android experiences. It is architected for individuals seeking sustainable dopamine regulation, executive dysfunction support, and mindful momentum without visual overstimulation.

The design movement balances **Modern Corporate Precision** with **Atmospheric Functional Minimalism**. Instead of aggressive gamification or jarring high-saturation alarms, the system operates with deliberate, low-arousal confidence. Depth is conveyed through deep slate structural planes and micro-luminance highlights, prioritizing ergonomic thumb-reach navigation, cognitive clarity, and measurable progress indicators.

## Colors

The palette relies on deep oceanic neutrals paired with purposeful, high-efficacy accent hues:

- **Surface Neutral Canvas (`#0B0F17`)**: Base canvas providing a restful backdrop to minimize ocular strain during prolonged evening or early-morning sessions.
- **Surface Containers**:
  - `surface-container-low`: `#131B28` for secondary card backings, inactive groups, and sheet backgrounds.
  - `surface-container-high`: `#1A2436` for active cards, elevated modal layers, and segmented pickers.
- **Productive Blue (`#2563EB`)**: Primary driver for actionable interactions, primary key buttons, key navigation nodes, and focused structural highlights.
- **Momentum Teal (`#14B8A6`)**: Secondary semantic anchor dedicated to dopamine loop feedback: completion rings, habit streaks, confirmed checkpoints, and biological rhythm counters.
- **Clarity Cyan (`#38BDF8`)**: Tertiary support for temporal progression, live intervals, and active timer readouts.
- **Subtle Boundaries**: All containers and interactive cells utilize a delicate boundary stroke of `rgba(255, 255, 255, 0.08)` to clearly articulate interface hierarchy without creating harsh visual compartmentalization.

## Typography

The typographic system creates an explicit functional dichotomy:
1. **Hanken Grotesk** anchors headings and titles with contemporary structural clarity and humanized balance.
2. **Inter** powers continuous reading experiences, descriptive guidance, and input elements, ensuring maximum legibility at compact mobile scale.
3. **JetBrains Mono** governs quantifiable behavioral mechanics: active session timers, numerical metrics, streak telemetry, and log metadata. This monospaced alignment eliminates layout jitter when countdowns and data points update in real time.

## Layout & Spacing

The layout model is constructed on an 8dp baseline grid with a 4dp micro-step for compact icon and badge alignments. 

- **Touch Ergonomics**: All interactive elements adhere to a strict minimum touch target of `48dp` (`3rem`), ensuring error-free operation even in high-stress or distraction-heavy contexts.
- **Mobile Handset Layout**: 4-column fluid system with `16dp` side margins and `16dp` gutters. Primary actions and navigation triggers occupy the lower screen threshold to accommodate single-hand reach.
- **Tablet / Foldable System**: Expands to an 8-column or 12-column adaptive layout with `24dp` outer margins, allowing behavioral metrics, habit tracks, and focus environments to dock side-by-side without vertical crowding.

## Elevation & Depth

Visual hierarchy does not rely on heavy drop shadows, keeping rendering crisp and distraction-free:

- **Tonal Layering**: Stacking occurs through sequential surface brightness:
  - Base: Canvas `#0B0F17`
  - Level 1 (Resting Cards & List Buckets): `#131B28`
  - Level 2 (Selected Cards, Modals, Menus): `#1A2436`
- **Linear Boundaries**: Elevation steps are reinforced with an outer hairline stroke of `1px solid rgba(255, 255, 255, 0.08)`.
- **Dynamic Luminance Glow**: When a task is completed, an active streak hits a milestone, or a focus timer runs, a subtle diffuse Momentum Teal (`rgba(20, 184, 166, 0.15)`) or Productive Blue (`rgba(37, 99, 235, 0.20)`) radial underglow is permitted behind the focal element, conveying kinetic feedback without overwhelming the interface.

## Shapes

The interface embraces a unified, balanced curvature language:

- **Cards and Structural Surfaces**: Standardized to `16dp` corner radii, creating a comfortable, softened appearance that remains aligned with Material 3 foundation standards.
- **Interactive Controls**: Buttons, input bars, bottom sheets, and dialogs utilize `12dp` to `16dp` radii depending on bounding height.
- **Status Chips & Pills**: Complete circular rounding (`9999dp`) for small contextual status trackers, category pills, and milestone badges.

## Components

### Buttons
- **Primary Kinetic Button**: Height `48dp` minimum. Background `#2563EB`, text color `#FFFFFF` (Inter Label-LG). Corner radius `12dp`. Active state lowers opacity to `0.9` with an inner border of `rgba(255, 255, 255, 0.2)`.
- **Secondary / Surface Button**: Height `48dp`. Background `#1A2436`, border `1px solid rgba(255, 255, 255, 0.08)`. Text `#E2E8F0`.

### Momentum Checkboxes & Radios
- **Checkbox**: `24dp` square box housed within a `48dp` interactive hit area. Unchecked state features an outline of `rgba(255, 255, 255, 0.2)` on `#131B28`. Checked state transitions seamlessly to Momentum Teal (`#14B8A6`) with a clean `#0B0F17` checkmark.
- **Radio Buttons**: Concentric rings following the same color rules with smooth cross-scale transitions.

### Cards & Habit Containers
- Surfaces use `#131B28` with a `1px` border of `rgba(255, 255, 255, 0.08)` and `16dp` corner radius. 
- Header areas pair Hanken Grotesk titles with JetBrains Mono cadence indicators (e.g., `+12 DAYS`, `25:00`).

### Input Fields
- Container height `56dp` with `#131B28` fill and `12dp` rounded corners. Default border is `rgba(255, 255, 255, 0.08)`. Focus shifts the border to Productive Blue (`#2563EB`) accompanied by a `2px` focus halo.

### Kinetic Completion Rings & Timers
- Circular stroke meters built with SVG/Canvas. Background track `#1A2436`, active progression track rendered in Momentum Teal (`#14B8A6`) or Clarity Cyan (`#38BDF8`).
- Centered numerical displays exclusively leverage JetBrains Mono (`metric-display`) for absolute anti-jitter temporal transitions.