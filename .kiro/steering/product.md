---
inclusion: always
---

# DopaShift — Product Overview

DopaShift is a multi-goal life-tracking platform (Android app + web portal) designed to help users reclaim distracted screen time and redirect it toward personal growth.

## Core Capabilities

- **Multi-Goal Framework:** Users define life goals (career, fitness, business, exams, etc.) with keywords, checklists, and linked habit programs.
- **Screen-Time Interception:** Monitors distraction apps against daily allowances; displays a full-screen overlay when time is depleted, surfacing productive alternatives (to-do items, habit checkboxes, educational videos).
- **Dynamic Content Recommendations:** Uses a BYO-LLM pipeline (OpenAI/Gemini/Claude) to suggest goal-relevant educational videos, with YouTube Data API as fallback.
- **Daily To-Do List:** Standalone task list (separate from goal checklists) with full CRUD, strikethrough completion, and cross-device sync.
- **30-Day Habit Roadmaps:** Automated habit-track builder with daily micro-habits, reminders, and escalation.
- **Offline-First Sync:** Append-only change-log with field-level conflict resolution for bidirectional device sync.
- **Flexible Reminders:** One-off, recurring, conditional, and escalating notifications with quiet-hours support.
- **Efficiency Auditing:** Daily scoring based on goal progress, habit completion, and screen-time metrics.

## Design Principles

- **Offline-first:** All data writes go to local storage immediately; sync is eventual and conflict-aware.
- **Cloud-agnostic:** No vendor lock-in; deployable via Docker Compose on commodity infrastructure.
- **Privacy-preserving:** Raw telemetry stays on-device; only aggregated/anonymized data syncs.
- **Solo/AI-agent development:** Requirements are explicit and unambiguous — no implementation-time judgment calls that change behavior.
- **Multilingual UI:** String translations supported (en, hi, mr); user-generated content stored in the language entered.
