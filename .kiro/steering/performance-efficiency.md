---
inclusion: auto
---

# Performance, Memory & Battery Efficiency Rules

## Non-negotiable rules

- Background monitoring (`UsageStatsManager` polling) is interval-based and batched — never a tight/continuous polling loop. Default interval: 5–10 seconds, configurable. Justify any deviation from this range explicitly.
- Battery-optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) is requested only for the specific interception foreground service, with a clear user-facing explanation — never a blanket exemption request for the whole app.
- Wake locks are held only for the minimum duration of an active interception check or overlay render — release immediately after, never held speculatively.
- Historical analytics data (charts, efficiency history) is lazy-loaded and paginated — never load full history into memory on screen open.
- Local database queries are indexed on frequently filtered fields (`user_id`, `goal_id`, `date`) — check for an index before shipping a query that filters/sorts on these fields.
- The background service has a profiled memory budget — profile before setting a number, don't guess one. Flag if a change measurably increases the service's memory footprint.
- Web portal bundles are code-split by route — the initial load (critical for the offline app-shell requirement) stays minimal. Don't add a heavy dependency to the initial bundle without confirming it's lazy-loadable.

## When generating background/service code specifically

Ask: does this run continuously, or can it be interval-based/event-driven? Default to the less continuous option unless the requirement explicitly demands real-time response.
