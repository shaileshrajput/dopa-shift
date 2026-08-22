---
name: DopaShift Kinetic
colors:
  surface: '#0b1326'
  surface-dim: '#0b1326'
  surface-bright: '#31394d'
  surface-container-lowest: '#060e20'
  surface-container-low: '#131b2e'
  surface-container: '#171f33'
  surface-container-high: '#222a3d'
  surface-container-highest: '#2d3449'
  on-surface: '#dae2fd'
  on-surface-variant: '#c2c6d8'
  inverse-surface: '#dae2fd'
  inverse-on-surface: '#283044'
  outline: '#8c90a1'
  outline-variant: '#424656'
  surface-tint: '#b3c5ff'
  primary: '#b3c5ff'
  on-primary: '#002b75'
  primary-container: '#0066ff'
  on-primary-container: '#f8f7ff'
  inverse-primary: '#0054d6'
  secondary: '#44e2cd'
  on-secondary: '#003731'
  secondary-container: '#03c6b2'
  on-secondary-container: '#004d44'
  tertiary: '#d0bcff'
  on-tertiary: '#3c0091'
  tertiary-container: '#8252ec'
  on-tertiary-container: '#fdf6ff'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#dae1ff'
  primary-fixed-dim: '#b3c5ff'
  on-primary-fixed: '#001849'
  on-primary-fixed-variant: '#003fa4'
  secondary-fixed: '#62fae3'
  secondary-fixed-dim: '#3cddc7'
  on-secondary-fixed: '#00201c'
  on-secondary-fixed-variant: '#005047'
  tertiary-fixed: '#e9ddff'
  tertiary-fixed-dim: '#d0bcff'
  on-tertiary-fixed: '#23005c'
  on-tertiary-fixed-variant: '#5516be'
  background: '#0b1326'
  on-background: '#dae2fd'
  surface-variant: '#2d3449'
  productive-blue: '#0066FF'
  momentum-teal: '#2DD4BF'
  focus-indigo: '#6366F1'
  intercept-overlay: rgba(15, 23, 42, 0.95)
  success-green: '#10B981'
  warning-amber: '#F59E0B'
  error-rose: '#E11D48'
typography:
  display-lg:
    fontFamily: Hanken Grotesk
    fontSize: 57px
    fontWeight: '700'
    lineHeight: 64px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 36px
  title-lg:
    fontFamily: Hanken Grotesk
    fontSize: 22px
    fontWeight: '500'
    lineHeight: 28px
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
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
  label-strikethrough:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  headline-lg-mobile:
    fontFamily: Hanken Grotesk
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 34px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  grid-margin: 24px
  gutter: 16px
  compact: 4px
  stack-sm: 12px
  stack-md: 24px
  stack-lg: 48px
---

## Brand & Style

The brand personality is **Disciplined, Professional, and Empowering**. It acts as a cognitive exoskeleton, helping users bridge the gap between intention and action. The design system is rooted in the **Corporate / Modern** aesthetic, specifically leveraging **Material Design 3 (M3)** principles to ensure a native, high-performance feel on Android while providing a sophisticated, data-rich experience on the web.

The emotional response should be one of **calm focus** followed by **kinetic momentum**. We achieve this through:
- **Precision Engineering:** Clean lines and systematic spacing that reflect a well-ordered mind.
- **Productive Redirection:** High-contrast overlays that feel authoritative but supportive, never punishing.
- **Visual Velocity:** Progress visualization (rings and streaks) that use vibrant, "living" colors against stable, neutral surfaces to celebrate small wins.

## Colors

The palette is anchored in **Deep Indigo and Energetic Teal**. The color system follows the M3 Tonal Palette approach but with higher saturation for progress indicators to maintain "dopamine" rewards during task completion.

- **Primary (Productive Blue):** Used for main actions, active streaks, and "Productive" state indicators.
- **Secondary (Momentum Teal):** Reserved for progress rings, completion checkmarks, and positive trends in efficiency auditing.
- **Tertiary (Focus Indigo):** Used for secondary categories, habit tracks, and educational content markers.
- **Neutral:** A deep navy-slate (`#0F172A`) serves as the base for dark mode, providing better contrast for energetic accents than pure black.

**Color Modes:** 
- **Dark (Default):** Surfaces use "Surface Container" logic (elevated levels are lighter).
- **Light:** Backgrounds are a clean, crisp grey-white (`#F8FAFC`) with primary colors remaining vibrant.

## Typography

The typography strategy balances high-end sans-serifs for readability with monospaced accents for technical data.

- **Hanken Grotesk (Headlines):** A sharp, contemporary grotesque that feels professional and athletic. Used for Efficiency Scores and Goal Titles.
- **Inter (Body):** The gold standard for UI legibility. Used for all core descriptions and task lists.
- **JetBrains Mono (Labels/Metadata):** Used for sync timestamps, LLM logs, and efficiency metrics to convey "precision" and data-driven insights.

**Styling Note:** Completed tasks must transition from `body-md` to `label-strikethrough`, reducing visual noise and providing immediate psychological closure.

## Layout & Spacing

This system utilizes a **12-column Fluid Grid** for web and a **4-column Fluid Grid** for mobile. The spacing rhythm is strictly 8px-based.

- **The Intercept Overlay:** Uses a "Focus Layout" with 48px vertical stacks between the three productive alternatives (To-do, Habit, Video). This intentional airiness prevents the user from feeling overwhelmed during a state of distraction.
- **Dashboard:** Uses a "Modular Grid" where cards span 6 columns on tablet and 3-4 columns on desktop, allowing for a dense but organized "at-a-glance" status.
- **Margins:** Standard 24px margins on mobile to ensure components don't feel "cramped" against the screen edge, reinforcing a premium feel.

## Elevation & Depth

The design system follows M3's **Tonal Layers** philosophy, supplemented by **Glassmorphism** for the Intercept Engine.

1.  **Level 0 (Base):** Neutral background.
2.  **Level 1 (Cards/Lists):** A subtle tonal shift (slightly lighter in dark mode) with a 1px low-contrast outline to define boundaries without heavy shadows.
3.  **Level 2 (Floating Action Buttons):** Subtle, extra-diffused ambient shadows tinted with the primary color (Productive Blue) to suggest interactivity.
4.  **Overlay Layer:** The Screen-Time Interception Engine uses a **Backdrop Blur (20px)** with a 95% opacity `intercept-overlay` tint. This creates a psychological "break" from the background app, making the redirection feel immersive and non-bypassable.

## Shapes

We use **Rounded (0.5rem)** as our base shape language to align with Material 3's approachable yet structured vibe.

- **Buttons & Checkboxes:** 0.5rem (8px) for a consistent "clickable" language.
- **Cards & Progress Containers:** 1rem (16px) for larger layout blocks (`rounded-lg`).
- **Progress Rings:** Always use rounded stroke caps to feel "organic" and fluid, representing the growth of a habit.
- **LLM Chips:** 2rem (`rounded-xl` or pill) to distinguish metadata from actionable tasks.

## Components

- **Completion Rings:** The centerpiece of progress. Use a dual-ring system: a low-opacity background track and a vibrant `momentum-teal` foreground stroke with rounded ends.
- **Intercept Overlay:** Full-screen container with high-blur backdrop. Content is center-aligned with a "Primary Alternative" highlighted in a Level 2 Card.
- **Task List Items:** 56px minimum height for touch targets. Left-aligned checkbox, center-aligned text, right-aligned drag handle. Transitions to 50% opacity upon completion.
- **Efficiency Charts:** Line graphs use a 2px `productive-blue` stroke with a subtle gradient fill underneath. Data points appear on hover/tap.
- **Interactive Chips:** Used for LLM providers (ChatGPT, Gemini, Claude). These should include the provider icon and use `label-md` for text.
- **Input Fields:** Outlined style with 1px border. Focus state uses a 2px `productive-blue` border and a floating label.
- **Action Buttons:**
    - **Primary:** Filled with `productive-blue`, white/light text.
    - **Secondary:** Outlined with `momentum-teal` for "Add" or "Increment" actions.
    - **Tertiary:** Text-only for "Skip" or "Settings" to maintain hierarchy.