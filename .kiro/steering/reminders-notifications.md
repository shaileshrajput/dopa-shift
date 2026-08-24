---
inclusion: auto
---

# Reminders & Notifications Rules

One shared reminder model applies to `DailyTodoItem`, goal-scoped checklist items, and habit-track checkpoints. Do not build per-entity-type reminder logic.

## Non-negotiable rules

- Reminders support: one-off (any date/time, no future limit), repeating (daily, specific weekdays, weekly, monthly, custom N-day interval), and conditional (fires only if a condition evaluates true at trigger time, e.g., "still incomplete by 6 PM").
- Conditional reminder rules are evaluated client-side against locally stored entity state (offline-capable), then reconciled against the backend once connectivity resumes — never require connectivity to evaluate a condition.
- Editing, completing, or deleting a reminder's underlying entity before it fires cancels/reschedules the reminder, including recomputing the next occurrence for a repeating reminder.
- Unacknowledged reminders (not opened/completed/dismissed within a configurable interval) re-trigger as a follow-up, up to a configurable maximum escalation count.
- Habit-track day misses get a dedicated escalation reminder distinct from the routine daily reminder.
- Reminder aggressiveness (frequency, escalation count, quiet hours) is user-configurable per entity, per goal, or globally. Quiet hours suppress all reminders regardless of escalation or condition state — this override always wins.
- Use exact alarms (`AlarmManager.setExactAndAllowWhileIdle` or equivalent) for any time-exact or escalating reminder — standard `WorkManager` deferred execution is not precise enough and must not be used where timing matters.
- Screen-time-depletion-triggered to-do reminders (fired when the intercept overlay triggers) are event-driven, not scheduled — keep this trigger path separate from the scheduled/repeating/conditional reminder engine, but route through the same notification delivery mechanism.
- All notification text is localized per the localization steering rules — never hardcode notification strings in one language.

## When generating reminder-related code

Confirm which of the three types (one-off, repeating, conditional) the feature needs before implementing — do not default to one-off if the requirement implies recurrence or a condition.
