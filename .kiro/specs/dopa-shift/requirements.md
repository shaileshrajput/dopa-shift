# Requirements Document

## Introduction

DopaShift is a multi-goal life-tracking system spanning an Android app and a web portal (Windows/iOS deferred to a later phase), built cloud-agnostic, offline-first, and horizontally scalable. The system lets a user define life goals (Career Growth, Fitness, Build Business, Clear Exam, etc.), intercepts distracting screen time with a non-bypassable-by-default overlay, recommends educational content dynamically, tracks habits via 30-day roadmaps, and syncs data bidirectionally across devices with field-level conflict resolution. The platform must ship with explicit, unambiguous specs — no requirement here should require an implementation-time judgment call that changes behavior.

**Assumptions:**
- Multilingual scope: UI-string translation is in scope; auto-translation of user-generated content (task/goal text) is explicitly OUT of scope for this version — stored and displayed in the language the user entered it.
- Backend queue mechanism: Postgres-backed job table (`SKIP LOCKED` pattern) rather than Kafka, per deferred-infrastructure decision.
- Deployment target for v1: Docker Compose, single or few hosts. Kubernetes manifests are out of scope for v1.
- BYO-LLM is used for three purposes: (1) goal/habit-preparation text suggestions, (2) video link suggestions with the actual video sourced via the link the LLM returns, and (3) habit-track content preparation. Video suggestions come directly from the user's own configured LLM (ChatGPT, Gemini, or Claude), not from a platform-side YouTube API call — YouTube Data API is retained only as a fallback when no LLM is configured or the LLM's response contains no parseable video link.

## Wireframe References

The following wireframes define the visual layout and interaction patterns for each major screen. These SHALL be used as the authoritative UI reference during the design phase.

| Screen | Wireframe Path | Maps to Requirement(s) |
|---|---|---|
| Dashboard | #[[file:.kiro/specs/wireframes/dashboard/code.html]] | Requirement 20 (Dashboard and Activity Overview) |
| Intercept Overlay | #[[file:.kiro/specs/wireframes/intercept_overlay/code.html]] | Requirements 2, 3, 6 (Interception Engine, Content Pipeline, Habit Roadmap) |
| Daily Tasks | #[[file:.kiro/specs/wireframes/daily_tasks/code.html]] | Requirement 4 (Daily To-Do List) |
| Goals & Habits | #[[file:.kiro/specs/wireframes/goals_habits/code.html]] | Requirements 1, 6 (Multi-Goal Framework, Habit Roadmap) |
| Create New Goal | #[[file:.kiro/specs/wireframes/create_new_goal/code.html]] | Requirement 1 (Multi-Goal Framework) |
| Habit Roadmap | #[[file:.kiro/specs/wireframes/habit_roadmap/code.html]] | Requirement 6 (Automated Habit Roadmap Builder) |
| Analytics & Audit | #[[file:.kiro/specs/wireframes/analytics_audit/code.html]] | Requirements 7, 18 (Efficiency Auditing, Traceability) |
| Notifications | #[[file:.kiro/specs/wireframes/notifications/code.html]] | Requirement 5 (Flexible Reminder Scheduling) |
| Settings & LLM | #[[file:.kiro/specs/wireframes/settings/code.html]] | Requirements 15, 19 (BYO-LLM, Profile Customization) |
| Design System | #[[file:.kiro/specs/wireframes/dopashift_kinetic/DESIGN.md]] | Requirement 17 (Professional UI/UX Design Standard) |

## Glossary

- **System**: The DopaShift platform as a whole (backend API, Android app, and web portal acting together).
- **Android_App**: The native Android client built with Kotlin and Jetpack Compose.
- **Web_Portal**: The React + TypeScript progressive web application.
- **Backend**: The Spring Boot (Kotlin) API server.
- **Goal_Profile**: A user-defined life-goal entity with a name, category, keywords, associated tasks, and habit programs.
- **DailyTodoItem**: A standalone daily to-do list entry, independent of any goal, supporting full CRUD and cross-device sync.
- **Goal_Checklist_Item**: A task/checklist entry scoped to exactly one Goal_Profile — distinct from DailyTodoItem.
- **Intercept_Overlay**: A full-screen `TYPE_APPLICATION_OVERLAY` displayed when a distraction app's daily allowance is depleted.
- **Habit_Track**: A 30-day sequence of micro-habit checkpoints tied to a Goal_Profile.
- **LLM_Provider**: A user-configured external AI service (OpenAI/ChatGPT, Google/Gemini, or Anthropic/Claude) accessed via the user's own API key.
- **Change_Log**: An append-only event store (`entity_id, field, value, timestamp, device_id, user_id`) used for bidirectional sync and conflict resolution.
- **Efficiency_Score**: A daily percentage computed from tracked productive vs. distracting foreground time.
- **Reminder**: A scheduled notification (one-off, repeating, or conditional) attachable to any DailyTodoItem, Goal_Checklist_Item, or Habit_Track checkpoint.
- **Correlation_ID**: A unique identifier generated at the origin of a user action or system event, propagated through all downstream calls for end-to-end traceability.
- **Audit_Log**: An immutable, append-only record of sensitive operations (goal deletion, API key changes, permission changes, account deletion).
- **Sync_Engine**: The shared infrastructure responsible for offline-first local writes, change-log event queuing, and field-level conflict resolution across devices.
- **Adapter_Layer**: A provider-agnostic abstraction (e.g., LiteLLM) that wraps each supported LLM provider behind a common interface.

## Requirements

### Requirement 1: Multi-Goal Framework

**User Story:** As a user, I want to define multiple life-goal profiles with associated keywords, tasks, and habit programs, so that I can track distinct areas of my life independently.

#### Acceptance Criteria

1. WHEN a user creates a goal profile, THE System SHALL require a name (1–100 characters, unique per user), a category (free-text, 1–50 characters), and at least one keyword.
2. WHEN a user adds keywords to a goal, THE System SHALL store them as a list associated with that goal, enforcing a maximum of 20 keywords per goal with each keyword being 1–50 characters, used for content recommendation matching.
3. WHEN a user creates a checklist task under a goal, THE System SHALL associate the task with exactly one goal — this Goal_Checklist_Item is a distinct entity from the DailyTodoItem defined in Requirement 4; the two SHALL NOT be merged into one data model.
4. IF a user has zero active goals, THEN THE System SHALL block creation of Habit_Tracks or interception rules that depend on a goal reference and display an error message indicating that at least one active goal is required.
5. WHEN a user requests deletion of a goal, THE System SHALL display a confirmation prompt listing all dependent items (Goal_Checklist_Items, Habit_Tracks, and interception rules) and require the user to choose one of: (a) delete all dependent items, or (b) reassign each dependent item to another existing goal selected by the user.
6. IF the user confirms goal deletion, THEN THE System SHALL execute the chosen action (delete or reassign) for every dependent item before removing the goal — orphaned references SHALL NOT be permitted.
7. IF a user attempts to create a goal with a name that already exists for that user, THEN THE System SHALL reject the creation and display an error message indicating the name is already in use.

---

### Requirement 2: Screen-Time Interception Engine

**User Story:** As a user, I want distracting apps/sites to be monitored against a daily time allowance with a full-screen intercept when the allowance is depleted, so that I can enforce my own limits.

#### Acceptance Criteria

1. WHEN the Android_App is installed THEN THE System SHALL request `PACKAGE_USAGE_STATS` and `SYSTEM_ALERT_WINDOW` via the manual Settings grant flow, since neither can be granted silently on Android 6.0+.
2. IF the device is running Android Go edition THEN THE System SHALL detect this and inform the user that overlay interception is unavailable on this device class, rather than failing silently.
3. WHEN a designated distraction app/site is in the foreground THEN THE System SHALL sample foreground status at intervals no greater than 1 second and accumulate elapsed time against the user-configured daily allowance for that app/site, where the configurable allowance ranges from 1 minute to 480 minutes in 1-minute increments.
4. WHEN the accumulated elapsed time for an app/site meets or exceeds its daily allowance THEN THE System SHALL display a full-screen Intercept_Overlay of type `TYPE_APPLICATION_OVERLAY` within 2 seconds of depletion detection.
5. WHEN the Intercept_Overlay is active THEN THE System SHALL NOT provide an in-overlay control that dismisses it before a minimum cooldown of 30 seconds has elapsed AND at least one alternative action (habit checkbox completion or video view of at least 10 seconds) has been performed.
6. IF the user revokes the overlay or usage-stats permission at the OS level THEN THE System SHALL detect the revocation within 5 seconds of the app returning to the foreground and notify the user that interception is disabled, rather than assume it is still active.
7. WHEN the Android_App targets Android 15+ THEN THE System SHALL ensure a visible overlay window exists before starting any background-triggered foreground service, per platform-enforced ordering.
8. THE System SHALL display a persistent informational notice accessible from the app's main settings screen stating that interception can be bypassed via OS-level permission revocation, force-stop, or OEM battery-management app killers — "non-bypassable" is scoped as "not bypassable through in-app UI," not as an absolute guarantee.
9. WHEN the Intercept_Overlay is displayed THEN THE System SHALL render the user's pending to-do list (open DailyTodoItems from Requirement 4) within the overlay, alongside the habit checkbox (Requirement 6) and video suggestion (Requirement 3); IF the pending to-do list is empty THEN THE System SHALL display a message indicating no pending tasks remain.
10. WHEN the daily allowance for an app/site is depleted THEN THE System SHALL, simultaneously with the overlay display, trigger a to-do list reminder notification (per Requirement 5) summarizing the user's pending tasks, so the reminder is visible even if the overlay is later dismissed via OS-level bypass (AC8).
11. WHEN a task is completed from within the Intercept_Overlay's to-do list view THEN THE System SHALL sync that completion through the same Change_Log pipeline as any other DailyTodoItem update (Requirement 4, AC4–AC5) — the overlay is a view onto the same DailyTodoItem data, not a separate list.
12. WHEN the local device clock passes midnight (00:00) in the user's configured time zone THEN THE System SHALL reset all accumulated elapsed times to zero, beginning a new daily allowance period for each tracked app/site.

---

### Requirement 3: Dynamic Content Recommendation Pipeline

**User Story:** As a user, I want educational video suggestions related to my active goal's keywords, sourced through my own LLM (ChatGPT, Gemini, or Claude), shown inside the intercept overlay, so that intercepted time redirects toward my goals using an AI provider I control.

#### Acceptance Criteria

1. WHEN the Intercept_Overlay is displayed AND the user has a configured LLM_Provider (Requirement 15) THEN THE System SHALL request video suggestions from that LLM_Provider, prompted with the active goal's name and keywords, requesting the response include direct video links, with a maximum request timeout of 10 seconds.
2. WHEN the LLM response contains one or more parseable video links (YouTube URL pattern) THEN THE System SHALL extract up to 3 links, validate each URL resolves to a real, embeddable video (server-side check via YouTube Data API's video-lookup endpoint using the extracted video ID — a validation/metadata call, not a search call), and display the first valid result via embedded playback.
3. IF the LLM response contains no parseable video link, OR all extracted links fail validation (private/deleted/region-blocked video) THEN THE System SHALL fall back to a server-side YouTube Data API keyword search (cached, goal-keyword-based, quota-managed) rather than show an error or blank overlay.
4. IF the user has no LLM_Provider configured THEN THE System SHALL use the YouTube Data API keyword-search pipeline as the sole source.
5. WHEN a recommendation request is made via the YouTube API (validation or fallback search) THEN THE System SHALL pass `relevanceLanguage` and `regionCode` derived from the user's stored locale; IF the user's locale is not supported by the YouTube API THEN THE System SHALL default to `en` and omit `regionCode`.
6. WHEN a video is selected/displayed inside the overlay THEN THE System SHALL play it via embedded playback without leaving the overlay context.
7. THE System SHALL cache LLM-suggested video links per goal-keyword-set for a configurable TTL (default 24 hours), to avoid calling the user's LLM_Provider on every single overlay trigger.
8. THE System SHALL log when a shown video came from the user's LLM_Provider vs. the YouTube-search fallback, viewable in an activity history accessible from settings — the fallback SHALL happen silently and automatically within the overlay itself with no user-facing interruption.

---

### Requirement 4: Daily To-Do List and Cross-Device Task Sync

**User Story:** As a user, I want a daily to-do list — separate from goal-linked checklist items — that I can check off, strike through, and edit, staying in sync between Android and the web portal regardless of which device I used last, so that I can act from anywhere.

#### Acceptance Criteria

1. THE System SHALL model the daily to-do list as a distinct entity (DailyTodoItem) from the Goal_Checklist_Item (Requirement 1, AC3) — a DailyTodoItem SHALL NOT require an associated goal and SHALL be creatable independently.
2. THE System SHALL support full CRUD on DailyTodoItems: create, edit (text up to 500 characters, due date/time), delete, and mark complete/incomplete — THE System SHALL enforce a maximum of 100 DailyTodoItems per user per day.
3. WHEN a DailyTodoItem is marked complete, THEN THE System SHALL render it with a checked checkbox and strikethrough text style, and SHALL retain it in a "completed" view rather than deleting it — the user SHALL be able to un-check it to revert.
4. WHEN a DailyTodoItem is created, edited, or completed on any client, THEN THE System SHALL persist the change to the local store within 200ms, regardless of connectivity.
5. WHILE connectivity is available, THE System SHALL push queued local changes to the Backend as append-only Change_Log events (`entity_id, field, value, timestamp, device_id, user_id`).
6. WHEN two clients edit different fields of the same DailyTodoItem while both are offline, THEN THE System SHALL merge both edits on sync — field-level merge, not whole-record Last-Write-Wins.
7. WHEN two clients edit the same field of the same DailyTodoItem while both are offline, THEN THE System SHALL resolve using a server-assigned logical timestamp (not device wall-clock time) and SHALL flag the losing edit as superseded, retrievable in a conflict history retained for at least 90 days rather than silently discarded.
8. WHEN a client reconnects, THEN THE System SHALL pull all Change_Log events since its last known sync point and apply them locally before allowing new local writes to sync — IF the pull does not complete within 30 seconds, THEN THE System SHALL allow local writes to sync and retry the pull using exponential backoff with a maximum of 5 retry attempts.
9. THE Goal_Checklist_Item (Requirement 1, AC3) SHALL use the same Sync_Engine and conflict-resolution rules (AC4–AC8) as the daily to-do list — the sync mechanism is shared infrastructure; only the data model and goal-association are different.
10. IF a sync push fails due to network error or server unavailability, THEN THE System SHALL retry using exponential backoff starting at 1 second, doubling per attempt, up to a maximum of 5 attempts — IF all attempts fail, THEN THE System SHALL retain the queued changes locally and display a sync-pending indicator to the user.
11. IF a DailyTodoItem is deleted on one client and edited on a different client while both are offline, THEN THE System SHALL treat the delete as authoritative — the edit SHALL be discarded, and the deleted item SHALL appear in the conflict history with the attempted edit noted.
12. IF a DailyTodoItem's text exceeds 500 characters on input, THEN THE System SHALL reject the input and display an error message indicating the maximum allowed length has been exceeded.

---

### Requirement 5: Flexible Reminder Scheduling and Rigorous Notifications

**User Story:** As a user, I want to set reminders on any day, at any time, as one-off or repeating, and optionally conditional, for to-do items, goal checklist items, and habit tracks alike, so that I don't rely on remembering to check the app, and missed activities are actively escalated rather than shown once and forgotten.

#### Acceptance Criteria

1. THE System SHALL support setting one or more Reminders on any entity type — DailyTodoItem (Requirement 4), Goal_Checklist_Item (Requirement 1, AC3), and Habit_Track checkpoint (Requirement 6) — using one shared reminder model, not a per-entity-type reimplementation.
2. THE System SHALL support one-off Reminders scheduled for any user-specified date and time at least 1 minute in the future, with no restriction on how far in the future.
3. THE System SHALL support repeating Reminders with configurable recurrence: daily, specific weekdays (e.g., Mon/Wed/Fri), weekly, monthly, or a custom interval (every N days, where N is between 1 and 365 inclusive).
4. THE System SHALL support conditional Reminders — a Reminder that fires only when one of the following conditions evaluates true at the scheduled time: (a) a linked DailyTodoItem is still incomplete, (b) a linked Goal_Checklist_Item is still incomplete, or (c) a linked Habit_Track checkpoint is still unchecked — rather than firing unconditionally at the scheduled time.
5. WHEN a Reminder's scheduled time arrives AND (for conditional Reminders) its condition evaluates true, THE System SHALL schedule a local notification on Android via `WorkManager`/`AlarmManager`.
6. WHILE the Web_Portal is open or has an active service worker registration, THE System SHALL deliver push notifications for the same scheduled, repeating, and conditional Reminders that would fire on Android.
7. WHEN a notification is generated, THE System SHALL localize its text using the user's stored locale preference via server-side `MessageSource`.
8. IF a Reminder's underlying entity (DailyTodoItem, Goal_Checklist_Item, Habit_Track checkpoint) is completed or deleted before the trigger fires, THEN THE System SHALL cancel the pending Reminder and, for a repeating Reminder on a completed entity, cancel only the current occurrence while preserving the next scheduled occurrence.
9. IF a Reminder's underlying entity is edited (e.g., due date changed) before the trigger fires, THEN THE System SHALL reschedule the Reminder to align with the updated entity state, including recomputing the next occurrence for a repeating Reminder.
10. WHEN a screen-time allowance is depleted and the Intercept_Overlay triggers (Requirement 2, AC10), THE System SHALL send a to-do list reminder notification summarizing up to the 5 highest-priority pending tasks — distinct from any scheduled Reminder in AC5, triggered by the interception event rather than a scheduled time.
11. WHEN a Reminder fires AND is not acknowledged (opened, completed, or dismissed) within a user-configurable interval (between 5 and 120 minutes, default 15 minutes), THE System SHALL re-trigger a follow-up escalation notification, up to a user-configurable maximum escalation count (between 1 and 10, default 3).
12. WHEN a Habit_Track day's micro-habit is not checked off by a user-configurable cutoff time (default: 1 hour before the user's configured quiet-hours start), THE System SHALL send a dedicated escalation Reminder distinct from the routine daily Reminder.
13. THE System SHALL let the user configure reminder aggressiveness per entity, per goal, or globally — including escalation interval (AC11), escalation count (AC11), and quiet hours defined as a daily start time and end time — and Reminders SHALL NOT fire during user-configured quiet hours; any Reminder or escalation suppressed by quiet hours SHALL be delivered at the end of the quiet-hours window.
14. THE System SHALL use exact alarms (`AlarmManager.setExactAndAllowWhileIdle` or equivalent) for Reminder and escalation timing, since standard `WorkManager` deferred execution is not guaranteed precise enough for time-exact or escalating Reminders.
15. THE System SHALL evaluate recurring and conditional Reminder rules client-side against locally stored entity state (offline-capable), and WHEN connectivity resumes, THE System SHALL reconcile local reminder state with the Backend using a last-write-wins strategy based on timestamps, consistent with the offline-first requirement (Requirement 8, AC4).
16. IF a Reminder's scheduled time arrives while the device is offline or powered off, THEN THE System SHALL deliver the notification immediately upon the device regaining an active state, and evaluate any conditional Reminder's condition at that delivery time rather than at the originally scheduled time.

---

### Requirement 6: Automated Habit Roadmap Builder

**User Story:** As a user, I want pre-configured 30-day habit tracks tied to a goal, with the current day's micro-habit shown inside the intercept overlay, so that habit-building is reinforced at the moment of distraction.

#### Acceptance Criteria

1. WHEN a user activates a Habit_Track for a goal THEN THE System SHALL initialize a 30-day sequence of micro-habit checkpoints tied to that Goal_Profile, with each checkpoint containing a description (1–200 characters) and a day number (1–30).
2. WHEN the Intercept_Overlay is displayed AND an active Habit_Track exists for the current goal THEN THE System SHALL render the current day's micro-habit checkbox and description inside the overlay.
3. WHEN the user checks off the current day's micro-habit THEN THE System SHALL record completion with a UTC timestamp, mark the checkpoint as completed, and advance the Habit_Track's current-day pointer to the next uncompleted day.
4. IF a day is missed (the user did not check off the micro-habit before midnight in the user's configured time zone) THEN THE System SHALL record that day's checkpoint as missed with the date, rather than skip silently — Habit_Track completion history SHALL be queryable for the analytics requirement (Requirement 7).
5. IF a user has multiple active Habit_Tracks for the same goal THEN THE System SHALL display only the Habit_Track with the earliest start date in the Intercept_Overlay; the user SHALL be able to view and complete other active tracks from the main habit screen.
6. WHEN all 30 days of a Habit_Track are completed or missed THEN THE System SHALL mark the Habit_Track as finished and remove it from the Intercept_Overlay rotation for that goal.
7. THE System SHALL sync Habit_Track checkpoint state (completed/missed/pending) across devices via the same Sync_Engine used for DailyTodoItems (Requirement 4, AC4–AC8).

---

### Requirement 7: Daily Efficiency Auditing and Analytics

**User Story:** As a user, I want a daily efficiency score with historical charts comparing productive vs. distracting time, so that I can see trends over time.

#### Acceptance Criteria

1. WHEN a day completes (local midnight, user's timezone) AND the user has at least 1 minute of total tracked foreground time for that day, THEN THE System SHALL compute an Efficiency_Score as an integer percentage (0–100%) equal to (total productive foreground seconds ÷ total tracked foreground seconds) × 100, rounded to the nearest whole number, and persist it locally.
2. IF the user has less than 1 minute of total tracked foreground time for a completed day, THEN THE System SHALL record that day's Efficiency_Score as unavailable and display a "No data" indicator instead of a numeric score.
3. WHEN the user views analytics, THEN THE System SHALL render historical bar charts and line graphs of daily Efficiency_Scores defaulting to the last 30 days, using locally stored scores and falling back to backend-synced history for date ranges not present locally.
4. IF the backend-synced history is unreachable when the user requests a date range not available locally, THEN THE System SHALL display the locally available scores and show an indicator that the remaining requested range is unavailable.
5. THE System SHALL compute Efficiency_Scores using the same formula and rounding regardless of which client (Android_App or Web_Portal) the underlying telemetry originated from, producing identical integer results for the same input data.

---

### Requirement 8: Local-First Data Privacy and Sync

**User Story:** As a user, I want raw tracking telemetry to stay local while only top-level metrics and configuration sync to the cloud, so that detailed behavioral data isn't unnecessarily centralized.

#### Acceptance Criteria

1. THE System SHALL store raw foreground-app telemetry and interception logs only in local device storage (Room/SQLite on Android, Dexie/IndexedDB on web) — this data SHALL NOT be transmitted to the Backend in raw form under any circumstance, including error reporting or diagnostics.
2. WHEN data syncs to the Backend THEN THE System SHALL transmit only aggregated top-level metrics (daily Efficiency_Score, task/goal/habit state, configuration) — not raw telemetry; the sync payload for a single day SHALL NOT exceed 50 KB.
3. THE System SHALL encrypt all data in transit via TLS 1.2 or higher and SHALL encrypt sensitive configuration/credential data (LLM API keys, OAuth tokens) at rest using AES-256 or equivalent.
4. THE System SHALL function fully for local task/goal/habit management with zero connectivity for an indefinite duration, syncing only when connectivity resumes.
5. WHEN connectivity resumes after an offline period, THE System SHALL complete sync of all queued changes within 60 seconds for queues of up to 500 pending events, using the Change_Log pipeline defined in Requirement 4.
6. IF local storage on the device reaches its capacity limit, THEN THE System SHALL notify the user and prioritize retention of the most recent 90 days of telemetry data, archiving or purging older records.

---

### Requirement 9: Cloud-Agnostic Infrastructure

**User Story:** As the system owner, I want the backend built only on self-hostable, portable components, so that the platform can run on any cloud provider or bare metal without redesign.

#### Acceptance Criteria

1. THE System SHALL use PostgreSQL (version 14 or higher) as the primary datastore — no proprietary managed-database APIs (e.g., DynamoDB, Firestore) SHALL be used anywhere in the backend.
2. THE System SHALL use S3-compatible object storage APIs (compatible with MinIO for self-hosted deployments) — no direct coupling to a single provider's storage SDK.
3. THE System SHALL use Keycloak (version 22 or higher) for authentication/authorization — no Firebase Auth or Cognito dependency.
4. THE System SHALL be fully containerized (Docker) and deployable via Docker Compose for v1, with a documented path to Kubernetes for later scaling — no compute logic SHALL depend on a specific cloud's serverless runtime.
5. THE System SHALL externalize all environment-specific configuration (database URLs, Redis endpoints, S3 endpoints, Keycloak URLs) via environment variables or mounted config files — no hardcoded infrastructure addresses SHALL exist in source code.
6. THE System SHALL use Redis in a manner compatible with any Redis-protocol-compliant server (including Valkey, KeyDB, or managed Redis offerings) — no provider-specific Redis extensions SHALL be used.

---

### Requirement 10: Generic Multi-Platform API

**User Story:** As a developer (or AI agent) building any client, I want one versioned API with no platform-specific branches, so that Android, web, and future clients consume identical contracts.

#### Acceptance Criteria

1. THE System SHALL expose a single versioned REST API (`/v1/...`) defined by an OpenAPI 3 specification that serves as the sole source of truth from which all client integrations are validated.
2. THE API SHALL NOT contain platform-conditional fields or endpoints (fields or endpoints whose presence, schema, or behavior varies based on the requesting client's platform) — platform-specific behavior SHALL be implemented client-side.
3. THE System SHALL authenticate all API requests via OAuth2/OIDC bearer tokens issued by Keycloak.
4. IF a request is made with a missing, malformed, or expired bearer token, THEN THE System SHALL reject the request with an unauthorized error response and SHALL NOT process the request body.
5. WHEN the API spec changes in a breaking way (removing or renaming an endpoint, removing or renaming a response field, changing a field's type, or changing a field from optional to required), THEN THE System SHALL introduce a new version path (e.g., `/v2/...`) and SHALL continue serving the previous version for a minimum of 6 months from the new version's release date.

---

### Requirement 11: Scalability

**User Story:** As the system owner, I want the backend able to scale horizontally as user count grows, without redesigning the architecture.

#### Acceptance Criteria

1. THE Backend SHALL be stateless at the API layer — session/auth state SHALL live in Keycloak/Redis, not in-process memory, so any instance can serve any request.
2. THE Change_Log/sync-event table SHALL be indexed by `user_id + timestamp` and designed for append-only writes, with archival of entries older than 90 days triggered when the table exceeds 1 million rows or on a scheduled weekly basis, whichever occurs first.
3. THE video-recommendation cache SHALL be keyed by goal-keyword set, not by user, with a TTL of 24 hours per entry and a maximum of 10,000 cached entries evicted by least-recently-used policy, so cache hit rate improves with user count rather than degrading.
4. THE System SHALL document a defined migration path from Docker Compose to Kubernetes and from a Postgres-backed job queue to Kafka, specifying the triggering thresholds for each step: Kubernetes migration at sustained load above 500 concurrent requests per second or more than 3 running instances, and Kafka migration when job queue depth consistently exceeds 10,000 pending items or p95 job processing latency exceeds 5 seconds over a 10-minute window.

---

### Requirement 12: Multilingual Support

**User Story:** As a user, I want the app interface in my preferred language, so that I can use it comfortably regardless of locale.

#### Acceptance Criteria

1. THE System SHALL store a `preferredLanguage`/`locale` field on the user profile as part of the core user entity, supporting values `en`, `hi`, and `mr`, with a default value of `en` applied when the field is not explicitly set.
2. THE Android_App SHALL localize all static UI strings via `res/values-<lang>/strings.xml`.
3. THE Web_Portal SHALL localize all static UI strings via `react-i18next`, switchable without a full page reload.
4. THE Backend SHALL localize server-generated text (notifications, error messages) via Spring `MessageSource`/`LocaleResolver`, keyed off the user's stored locale.
5. THE System SHALL NOT auto-translate user-generated content (goal names, task text) — such content SHALL be stored and displayed exactly as entered.
6. IF a translation string is missing for the user's locale, OR the user's stored locale value is not one of the supported values (`en`, `hi`, `mr`), THEN THE System SHALL fall back to English rather than rendering a blank or key-name string.
7. WHEN a user changes their preferred locale in settings, THE System SHALL reflect the new locale in all UI strings and server-generated text without requiring re-authentication or app restart.

---

### Requirement 13: Enterprise/Production-Grade Quality

**User Story:** As the system owner, I want production-grade engineering practices from the start, so that the system is maintainable, secure, and observable as it grows.

#### Acceptance Criteria

1. THE Backend SHALL have automated tests (unit via JUnit5/Kotest, integration via Testcontainers against real Postgres/Redis) running in CI on every pull request and merge to the main branch, with a minimum line coverage threshold of 80% enforced as a CI gate.
2. THE Android_App SHALL have automated tests via Espresso/Compose Test; the Web_Portal via Jest and Playwright, with a minimum line coverage threshold of 70% enforced as a CI gate.
3. THE System SHALL run dependency vulnerability scanning (OWASP dependency-check or equivalent) in CI, and IF a vulnerability of severity Critical or High (CVSS score 7.0 or above) is detected, THEN THE System SHALL fail the CI pipeline and prevent the merge.
4. THE System SHALL expose Prometheus-compatible metric endpoints including at minimum: request rate, error rate, response latency (p50, p95, p99), and JVM/runtime resource utilization, and these metrics SHALL be queryable in Grafana dashboards.
5. THE System SHALL implement distributed tracing via OpenTelemetry across API request handling and background-job execution paths, such that each inbound API request and each background job execution produces a trace with a unique trace ID propagated across all downstream service calls.
6. THE System SHALL centralize logs (ELK or Loki stack) rather than rely on per-instance log files, with logs searchable by trace ID, timestamp, service name, and severity level, and retained for a minimum of 30 days.
7. THE System SHALL manage secrets via a dedicated secrets mechanism (Vault or sealed secrets) — no credentials SHALL be committed to source control or stored in plaintext config.
8. WHEN a commit or pull request is submitted, THE System SHALL run automated secret scanning in CI, and IF a credential or secret pattern is detected in source files, THEN THE System SHALL fail the pipeline and prevent the merge.

---

### Requirement 14: Architecture Pattern Compliance

**User Story:** As the system owner, I want the codebase to follow Clean/Hexagonal Architecture, so that business logic is platform-independent and testable in isolation.

#### Acceptance Criteria

1. THE domain layer (goal, task, habit, efficiency-scoring logic) SHALL contain no import statements or references to Spring, Android SDK, or React framework packages, verified by compiling the domain module in isolation without framework dependencies on the classpath or module resolution path.
2. THE System SHALL implement the Repository pattern such that domain logic references only repository interfaces defined within the domain layer, and concrete storage or API implementations reside in an outer infrastructure layer that the domain module does not depend on.
3. THE System SHALL implement the Strategy pattern for conflict-resolution and OAuth-provider logic such that a new strategy implementation can be introduced by implementing the existing strategy interface without modifying any calling code.
4. THE System SHALL implement reactive/observer-based local data propagation (Kotlin Flow on Android, signals/RxJS on web) so that UI components subscribe to observable data streams and receive updates from local DB changes within 2 seconds without issuing periodic polling requests.
5. THE System SHALL implement the Adapter pattern to wrap each external OAuth provider behind a single common authentication-provider interface, so that consuming code depends only on that interface and requires no conditional branching per provider.
6. IF the domain module is compiled or tested in isolation, THEN THE System SHALL produce no unresolved references to framework-specific types, confirming that domain unit tests execute without any framework runtime or test container.

---

### Requirement 15: Bring-Your-Own-LLM Integration

**User Story:** As a user, I want to use my own ChatGPT, Gemini, or Claude account/API key for goal suggestions, habit preparation, and video recommendations, so that I control which AI provider processes my data and which provider sources my content suggestions.

#### Acceptance Criteria

1. THE System SHALL let a user configure exactly one active LLM_Provider (OpenAI/ChatGPT, Google/Gemini, or Anthropic/Claude) at a time by supplying their own API key (maximum 256 characters) in account settings.
2. THE System SHALL store the user's LLM API key encrypted at rest (via the secrets-management mechanism defined in Requirement 13) and SHALL NOT log it in plaintext anywhere, including error logs.
3. THE System SHALL route all LLM calls through a provider-agnostic Adapter_Layer (e.g., LiteLLM or an equivalent internal adapter) so switching providers requires no logic change — this directly reuses the Adapter pattern already required in Requirement 14.
4. WHEN a user requests AI-assisted goal-definition or habit-preparation suggestions (e.g., goal description, keyword ideas, 30-day Habit_Track content), THE System SHALL call the user's configured LLM_Provider with only the following context fields: goal name, goal description, and user-supplied keywords, and SHALL NOT fall back to a platform-funded LLM without explicit user configuration of one.
5. WHEN the System requests video suggestions from the user's LLM_Provider (Requirement 3), THE System SHALL prompt for direct video links and SHALL validate any returned link server-side (Requirement 3, AC2) before display; IF the configured provider's model does not have live web-search/browsing capability, THEN THE System SHALL automatically fall back to the YouTube-search-only video pipeline for that request and SHALL indicate to the user that video suggestions used search fallback due to provider limitations.
6. IF no LLM_Provider is configured, THEN THE System SHALL disable AI-assisted suggestion features gracefully (hidden or clearly labeled as unavailable) and SHALL use the YouTube-search-only video pipeline, rather than error.
7. WHEN an LLM call is made, THE System SHALL send only the goal name, goal description, and user-supplied keywords as context, and SHALL NOT send the user's full task history, telemetry data, or personally identifiable information beyond what is contained in those fields — this is a privacy boundary consistent with Requirement 8's local-first telemetry principle.
8. WHEN a user submits an API key for validation, THE System SHALL issue a lightweight test call to the selected provider and SHALL return a success or failure result within 10 seconds; IF the call does not respond within 10 seconds, or returns an authentication error, or returns a rate-limit response, THEN THE System SHALL display an error message indicating the specific failure reason (timeout, invalid key, or rate limit exceeded) and SHALL NOT save the key.
9. THE System SHALL surface to the user, in settings, which of the three supported providers (ChatGPT, Gemini, Claude) are documented as having live web-search/browsing capability, since this directly affects whether video-link suggestions (AC5) are likely to return valid results versus needing the YouTube-search fallback — this is informational guidance, not a hard restriction on provider choice.
10. IF an LLM call fails at runtime due to network error, provider outage, or rate limiting, THEN THE System SHALL display a non-blocking notification to the user indicating the failure reason, SHALL NOT retry more than 2 additional times with a 3-second delay between attempts, and SHALL fall back to non-AI functionality (e.g., YouTube-search-only for videos) if all retries are exhausted.

---

### Requirement 16: Performance, Memory, and Battery Efficiency

**User Story:** As a user, I want the app to run efficiently in the background without draining battery or consuming excessive memory, so that I'm not deterred from keeping interception and tracking active at all times.

#### Acceptance Criteria

1. THE Android_App background monitoring service SHALL use batched/interval-based `UsageStatsManager` polling (not continuous tight-loop polling) — polling interval SHALL be configurable between 3 seconds and 30 seconds, and SHALL default to 5 seconds.
2. THE System SHALL request exemption from OS battery optimization (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) only for the specific foreground service required for interception, with a user-facing explanation that states the service name and the reason the exemption is needed (continuous app-usage monitoring) — not a blanket exemption request.
3. THE Android_App SHALL release wake locks within 500 milliseconds of completing an active interception check or overlay render cycle.
4. THE System SHALL lazy-load and paginate historical analytics data (Requirement 7) in pages of no more than 50 records rather than loading full history into memory on screen open.
5. THE local database queries (Room/Dexie) SHALL be indexed on frequently filtered fields (`user_id`, `goal_id`, `date`) to avoid full-table scans on analytics/chart rendering.
6. WHILE the Android_App background service is running, THE System SHALL keep heap memory consumption of the background service at or below 64 MB under normal operation (monitoring up to 20 tracked apps concurrently).
7. THE Web_Portal SHALL code-split routes/bundles so the initial app-shell load transfers no more than 150 KB of compressed JavaScript to the client.
8. WHILE the Android_App background service has been running for 1 hour or more, THE System SHALL not have increased device battery consumption by more than 3% per hour attributable to the app (as reported by Android battery usage statistics).

---

### Requirement 17: Professional UI/UX Design Standard

**User Story:** As a user, I want the app's visual design and interaction quality to match established, polished habit/goal-tracking apps, so that the product feels credible and pleasant to use daily.

#### Acceptance Criteria

1. THE System SHALL follow a consistent design system (spacing scale with a base unit of 4dp/4px, typography scale with at minimum 5 defined levels, color tokens for primary/secondary/surface/error/background) rather than ad-hoc per-screen styling — Material Design 3 for Android (native fit with Jetpack Compose), a matching design-token system for the Web_Portal.
2. THE System SHALL provide light and dark theme support, togglable by the user or following the device system setting, with all screens rendering correctly in both themes without unreadable text or invisible elements.
3. THE habit/goal tracking screens SHALL render at least one progress visualization element (streak counter, completion ring, progress bar, or chart) as a primary UI element visible without scrolling on the goal detail and habit detail screens.
4. THE onboarding flow SHALL consist of no more than 5 screens guiding the user through goal selection and first habit setup, completable in under 2 minutes, rather than a blank-state dashboard on first launch.
5. THE System SHALL treat the named apps (Habitify, Streaks/Calistree, Fabulous) as a quality and interaction-pattern reference only; no visual assets or copyrighted UI elements from those apps SHALL be reproduced.
6. THE System SHALL implement the "DopaShift Kinetic" design system as defined in the wireframe design document, using the specified color palette (Productive Blue #0066FF, Momentum Teal #2DD4BF, Focus Indigo #6366F1), typography (Hanken Grotesk for headlines, Inter for body, JetBrains Mono for labels/metadata), and 8px-based spacing rhythm.
7. THE System SHALL implement glassmorphism effects (backdrop-filter blur with semi-transparent backgrounds) for the Intercept_Overlay and elevated cards, consistent with the wireframe design system's "Elevation & Depth" specification.
8. THE navigation structure SHALL follow the wireframe pattern: bottom navigation bar on mobile (Home, Goals, Analytics, Settings) and a persistent navigation drawer on desktop/web.

---

### Requirement 18: Traceability, Logging, and Monitoring

**User Story:** As the system owner, I want end-to-end traceability of user actions and system events across all components, structured audit logging for sensitive operations, and real-time monitoring with alerting, so that issues can be diagnosed quickly, security-relevant changes are permanently recorded, and system health is proactively managed — complementing the infrastructure-level observability already defined in Requirement 13.

#### Acceptance Criteria

1. WHEN a user action or system event originates on any client (Android_App, Web_Portal) THEN THE System SHALL generate a unique Correlation_ID at the point of origin and SHALL propagate it through every downstream service call, background job, and database write associated with that action — enabling full request-path reconstruction from a single ID.
2. THE System SHALL emit structured log entries (JSON format) from all components (Android_App, Web_Portal, Backend, background jobs) — each entry SHALL include at minimum: timestamp (ISO 8601, UTC), Correlation_ID, service name, log level, user ID (when authenticated), and a machine-parseable event type field.
3. WHEN a sensitive operation is performed — including but not limited to: goal deletion (Requirement 1, AC5), LLM API key creation/rotation/deletion (Requirement 15), permission grant/revocation changes in Keycloak, user account deletion, and reminder-aggressiveness configuration changes — THEN THE System SHALL write an immutable Audit_Log entry containing: the Correlation_ID, authenticated user ID, operation type, timestamp, affected entity ID, and before/after state (where applicable).
4. THE Audit_Log entries SHALL be stored in an append-only, tamper-evident store (separate table or index from general application logs) and SHALL NOT be deletable via standard application APIs — only a documented administrative retention-policy process SHALL remove expired audit entries.
5. WHEN a log entry or Audit_Log entry is written THEN THE System SHALL enforce a retention policy: application logs SHALL be retained for a configurable duration (default 30 days), Audit_Log entries SHALL be retained for a longer configurable duration (default 1 year) — expired entries SHALL be archived or purged by an automated background job.
6. THE centralized log store (ELK/Loki, per Requirement 13 AC6) SHALL index logs by Correlation_ID, user ID, service name, and event type — enabling sub-second search across all components by any of these dimensions.
7. THE System SHALL define Grafana dashboard panels (building on Requirement 13 AC4) covering: API request rate and latency percentiles (p50/p95/p99), sync-event queue depth and processing lag, background-job success/failure rates, active-user count, and interception-overlay trigger rate — grouped by service and goal category where applicable.
8. WHEN a monitored metric crosses a defined threshold THEN THE System SHALL fire an alert via a configurable channel (webhook, email, or messaging integration) — thresholds SHALL be defined for at minimum: API error rate exceeding 5% over a 5-minute window, sync-queue depth exceeding 1000 messages, background-job failure rate exceeding 10% over a 10-minute window, and Audit_Log write failures (any occurrence).
9. THE Android_App SHALL log structured events locally (to the local database, consistent with Requirement 8 AC1's local-first telemetry principle) and SHALL sync only aggregated diagnostic summaries (crash counts, sync-failure counts, overlay-trigger counts) to the Backend every 24 hours or on next successful sync, whichever comes first, with a maximum summary payload of 10 KB — raw client-side structured logs SHALL NOT leave the device.
10. THE Web_Portal SHALL emit structured browser-side log events to the Backend via a lightweight telemetry endpoint at a maximum rate of 10 events per minute per session — these events SHALL carry the same Correlation_ID propagated from the originating user action.
11. WHEN distributed tracing is active (Requirement 13 AC5, OpenTelemetry) THEN THE System SHALL embed the Correlation_ID as a trace attribute/baggage item, so that traces and structured logs can be cross-referenced without separate correlation logic.
12. IF an Audit_Log write fails (e.g., due to storage unavailability) THEN THE System SHALL retry with exponential backoff (maximum 5 retries, maximum backoff delay 60 seconds) and SHALL NOT silently drop the entry — if retries are exhausted, the System SHALL fire a critical alert (AC8) and SHALL queue the entry for deferred write, retaining the deferred queue for up to 72 hours upon storage recovery.
13. THE Audit_Log store SHALL implement tamper-evidence verification via hash chaining (each entry includes a hash of the previous entry) or equivalent mechanism, such that any modification or deletion of a stored entry is detectable by an integrity-check process.

---

### Requirement 19: User Profile Customization

**User Story:** As a user, I want to upload a profile photo, change my password, and customize the app's accent color, so that my experience feels personalized and my account security is self-managed.

#### Acceptance Criteria

##### Profile Photo

1. WHEN a user uploads a profile photo THEN THE System SHALL accept only JPEG or PNG format files with a maximum file size of 5 MB.
2. WHEN a valid profile photo is uploaded THEN THE System SHALL store it via S3-compatible object storage (consistent with Requirement 9, AC2) and SHALL associate the stored object URL with the user's profile entity.
3. WHEN a user changes their profile photo THEN THE System SHALL replace the previous photo object in storage and update the profile reference — the previous object SHALL be deleted to avoid orphaned storage.
4. WHEN a user removes their profile photo THEN THE System SHALL delete the stored object and revert the user's displayed avatar to a system-generated default (initials-based or generic silhouette).
5. THE System SHALL display the user's profile photo consistently across Android_App (Jetpack Compose `AsyncImage` or equivalent) and the Web_Portal — both clients SHALL fetch the photo via the same S3-compatible URL stored on the profile entity.
6. WHILE a user has no profile photo set THEN THE System SHALL display a deterministic default avatar derived from the user's display name initials and a stable background color, rather than a blank or broken-image placeholder.

##### Password Change

7. WHEN a user initiates a password change THEN THE System SHALL route the request through Keycloak's Account Management API (consistent with Requirement 9, AC3 and Requirement 10, AC3) — the System SHALL NOT implement a custom password-storage or verification mechanism outside Keycloak.
8. WHEN a password change is requested THEN THE System SHALL require the user to provide their current password for verification before accepting the new password.
9. WHEN a new password is submitted THEN THE System SHALL enforce Keycloak's configured password policy (minimum length, complexity rules, password history) and SHALL surface policy-violation errors to the user in a human-readable, localized form.
10. WHEN a password change succeeds THEN THE System SHALL invalidate all existing sessions for that user (across all devices) except the session from which the change was initiated — forcing re-authentication on other clients.
11. IF the current-password verification fails THEN THE System SHALL reject the request with a clear error and SHALL NOT reveal whether the failure was due to an incorrect password versus a locked account — consistent with secure authentication error practices.

##### Theme Accent Color

12. THE System SHALL let the user select an accent color from a predefined palette of at least 12 colors OR enter a custom hex color code (#RRGGBB) — this accent color is distinct from the light/dark mode toggle defined in Requirement 17, AC2.
13. WHEN the user selects or enters an accent color THEN THE System SHALL persist the choice to the user's profile entity and SHALL sync it across devices via the standard sync mechanism (Requirement 4, AC4–AC5).
14. WHEN a custom hex color is entered THEN THE System SHALL validate that the value is a well-formed 6-digit hexadecimal color code and SHALL reject invalid input with an inline validation error.
15. THE Android_App SHALL apply the selected accent color using Material Design 3 dynamic color theming (custom `ColorScheme` seed) — the accent color SHALL propagate to primary interactive elements (buttons, toggles, progress indicators, navigation highlights) consistently across all screens.
16. THE Web_Portal SHALL apply the selected accent color via CSS custom properties (design tokens) — the accent color SHALL propagate to primary interactive elements consistently, matching the Android_App's visual treatment.
17. WHILE no accent color has been explicitly selected by the user THEN THE System SHALL apply a default accent color defined in the design system's base theme — the app SHALL NOT appear un-themed or inconsistent on first use.

---

### Requirement 20: Dashboard and Activity Overview

**User Story:** As a user, I want a central dashboard that aggregates and summarizes all my activity across goals, habits, to-do items, and efficiency scores, with quick-action shortcuts, so that I can see my current status at a glance and act on it without navigating to individual screens.

#### Acceptance Criteria

1. THE System SHALL provide a Dashboard screen on both Android_App and Web_Portal that serves as the primary landing screen after authentication, aggregating summary data from Goal_Profiles, DailyTodoItems, Habit_Tracks, and Efficiency_Scores into a single scrollable view.
2. WHEN the Dashboard is displayed, THE System SHALL render a "Today's Status" section containing: (a) count of pending (incomplete) DailyTodoItems for the current day, (b) count of active Habit_Tracks with their current-day checkpoint status (completed, pending, or missed), (c) a summary of Goal_Profile progress (number of completed vs. total Goal_Checklist_Items per active goal), and (d) the current day's Efficiency_Score or a "No data" indicator if unavailable (consistent with Requirement 7, AC2).
3. WHEN the Dashboard is displayed, THE System SHALL render an Efficiency_Score trend indicator showing the direction of change (improving, declining, or stable) computed by comparing the current day's score (or most recent available score) against the average of the preceding 7 days — IF fewer than 2 days of score data exist, THEN THE System SHALL display a "Not enough data" indicator instead of a trend.
4. THE Dashboard SHALL provide quick-action controls that allow the user to: (a) mark a DailyTodoItem as complete directly from the dashboard without navigating to the to-do list screen, and (b) check off the current day's Habit_Track micro-habit directly from the dashboard without navigating to the habit detail screen — these actions SHALL use the same data pipeline and sync mechanism as actions performed on their respective dedicated screens (Requirement 4, AC4–AC5; Requirement 6, AC3).
5. WHEN a quick-action is performed on the Dashboard, THE System SHALL update the Dashboard view optimistically (reflecting the change immediately in the UI) and SHALL persist the change to the local store within 200ms, consistent with Requirement 4, AC4.
6. THE Dashboard SHALL display a recent activity feed showing the last 20 user actions across all entity types (DailyTodoItem completions, Goal_Checklist_Item updates, Habit_Track checkpoint completions, goal creations/deletions), ordered by timestamp descending, with each entry showing: action type, entity name, and relative timestamp (e.g., "2 minutes ago").
7. WHEN a new action is performed on any client, THE System SHALL append it to the activity feed data and display it on the Dashboard within 2 seconds on the originating device, consistent with the reactive data propagation defined in Requirement 14, AC4.
8. WHILE the device has no connectivity, THE System SHALL render the Dashboard using locally cached data (local Room/Dexie store) — all summary counts, trend indicators, quick-actions, and activity feed entries SHALL remain functional offline, consistent with Requirement 8, AC4.
9. WHEN connectivity resumes after an offline period, THE System SHALL refresh the Dashboard data by pulling synced changes via the Change_Log pipeline (Requirement 4, AC8) and updating all displayed summaries to reflect the merged state.
10. THE Dashboard SHALL paginate the activity feed beyond the initial 20 entries, loading additional entries in pages of 20 on user scroll, using the same lazy-loading pattern defined in Requirement 16, AC4.
11. IF the user has zero Goal_Profiles, zero DailyTodoItems, and zero active Habit_Tracks, THEN THE System SHALL display an empty-state Dashboard with contextual prompts guiding the user to create their first goal, add a to-do item, or start a habit track — rather than rendering a blank or data-less screen.
12. THE System SHALL expose Dashboard summary data via the versioned REST API (Requirement 10, AC1) as a single aggregated endpoint, so that both Android_App and Web_Portal consume the same contract for Dashboard rendering — platform-specific layout and styling differences SHALL be implemented client-side only.
13. THE Dashboard SHALL display active Goal_Profiles as cards in a modular grid layout (2 columns on tablet, 3-4 on desktop), each showing: goal name, category icon, streak count, progress ring with percentage, and a brief description.
14. THE Dashboard SHALL include a "Today's Focus" section showing the daily to-do list with inline quick-add input, consistent with the wireframe layout.
15. THE Dashboard header SHALL display the user's daily Efficiency_Score as a prominent circular progress indicator with percentage and trend comparison to the previous day.

---

### Requirement 21: Testing Framework and Strategy

**User Story:** As the system owner, I want a defined, enforced testing strategy across every layer and every requirement, so that AI-agent-driven implementation (Kiro) does not skip test coverage under implementation pressure, and regressions are caught before release.

#### Acceptance Criteria

1. THE System SHALL generate one automated test per Acceptance Criteria line defined in the requirements document — no implementation task for a given Acceptance Criteria SHALL be marked complete without a corresponding passing test.
2. THE Backend SHALL use JUnit5/Kotest for unit tests covering domain logic (goal/habit/task entities, efficiency scoring, conflict-resolution merge logic) with no Spring context loaded, for fast feedback.
3. THE Backend SHALL use Testcontainers for integration tests, running real PostgreSQL and Redis instances per test run rather than mocks, to catch schema/query-level defects unit tests cannot.
4. THE Android_App SHALL use Espresso and Compose Test for instrumented UI tests, covering overlay rendering, checkbox/strikethrough interactions, and permission-request flows (via UiAutomator for system dialogs).
5. THE Web_Portal SHALL use Jest for component/unit tests and Playwright for end-to-end tests, including simulated offline/network-throttled scenarios.
6. THE System SHALL maintain a dedicated, deterministic test suite for the Sync_Engine/conflict-resolution engine (Requirement 4) that simulates two or more clients editing offline (same field and different fields), reconnecting in varying orders, and asserts the merge outcome matches the field-level merge rule on every run — this suite SHALL be treated as higher-priority than general CRUD test coverage given the data-loss risk of an undetected regression.
7. THE System SHALL test recurring and conditional Reminder logic (Requirement 5) using an injectable/fake clock rather than real wall-clock delays — tests SHALL be able to fast-forward simulated time to validate multi-day recurrence and escalation without real-time waiting.
8. THE System SHALL validate the OpenAPI contract (Requirement 10) via automated contract tests, failing CI if any endpoint response diverges from the published spec.
9. THE System SHALL test the BYO-LLM integration (Requirement 15) against mocked provider responses covering: valid response, invalid/expired API key, rate-limit response, and a response with no parseable video link — to validate the fallback-to-YouTube-search path (Requirement 3, AC3) is exercised and correct.
10. THE System SHALL test the screen-time interception engine (Requirement 2) for permission-revocation handling, Android Go-edition detection, and overlay-trigger-to-notification consistency, using emulator/device configurations representing each supported Android API level range, not a single test device profile.
11. THE CI pipeline SHALL run unit and integration tests on every commit/pull request; end-to-end tests (Playwright, full Android instrumented suites) SHALL run at minimum before any release build, given their longer execution time.
12. THE System SHALL enforce a minimum code-coverage threshold on the domain/business-logic layer (Clean Architecture core, Requirement 14) — exact percentage SHALL be set by the team; THE System SHALL NOT treat coverage percentage as sufficient proof of correctness for the Sync_Engine and conflict-resolution suites (AC6), which SHALL additionally require scenario-based assertions regardless of raw coverage number.
13. THE System SHALL fail the CI build if any test suite fails — no requirement or task SHALL be marked complete on a red test run.

---

### Requirement 22: Security Framework

**User Story:** As the system owner, I want a defined security framework covering authentication, data protection, and secure development practices, so that user data (including BYO-LLM API keys and offline-cached telemetry) is protected end-to-end.

#### Acceptance Criteria

1. THE System SHALL authenticate all API requests via OAuth2/OIDC bearer tokens issued by Keycloak (Requirement 9, AC3) — no custom/home-grown authentication mechanism SHALL be implemented.
2. THE System SHALL encrypt all data in transit via TLS 1.2 or higher, for every client-to-Backend connection (Android_App, Web_Portal) and every Backend-to-Backend connection (API to PostgreSQL, Redis, Keycloak).
3. THE System SHALL encrypt sensitive data at rest, specifically: user authentication credentials (delegated to Keycloak's own storage), BYO-LLM API keys (Requirement 15, AC2), and any other secret/credential data — via the secrets-management mechanism (Vault or sealed secrets, Requirement 13, AC7).
4. THE System SHALL NOT log sensitive data in plaintext at any log level, specifically: LLM API keys, auth tokens, and any personally identifiable user data — log redaction/masking SHALL be implemented at the logging-framework level, not left to per-call developer discipline.
5. THE System SHALL run automated dependency vulnerability scanning (OWASP Dependency-Check or equivalent, Requirement 13, AC3) in CI on every build, failing the build on critical/high-severity findings.
6. THE System SHALL run static application security testing (SAST) — e.g., Semgrep, SonarQube's security rule set, or equivalent OSS tool — in CI against both Backend (Kotlin/Spring) and Web_Portal (TypeScript/React) code.
7. THE System SHALL validate and sanitize all user-supplied input server-side (goal names, task text, keywords) regardless of client-side validation, to prevent injection attacks (SQL injection mitigated via parameterized queries/ORM, XSS mitigated via output encoding in the Web_Portal).
8. THE System SHALL implement rate limiting on all public API endpoints, with stricter limits on authentication endpoints specifically, to mitigate credential-stuffing and brute-force attempts.
9. THE System SHALL scope BYO-LLM API key usage narrowly — outbound calls to the user's configured LLM_Provider SHALL include only the minimum necessary context (Requirement 15, AC7) and SHALL NOT transmit raw local telemetry (Requirement 8, AC1) under any circumstance.
10. THE System SHALL implement Role-Based Access Control (RBAC) at the API layer, even though v1 has a single user role, so the authorization model does not require rearchitecting when additional roles (e.g., admin, support) are introduced later.
11. THE System SHALL enforce that a user can only read/write their own data (user_id scoping) at the API/query layer — every data-access query SHALL be scoped by authenticated user identity, not filtered client-side only.
12. THE System SHALL support API key/credential rotation for BYO-LLM provider keys — a user SHALL be able to update or remove their configured API key at any time, with the old key immediately invalidated from further use by the System.
13. THE System SHALL conduct a security review (manual or automated dependency/config audit) before each production release — exact cadence and tooling beyond what is listed above SHALL be defined by the team based on release frequency.
14. THE System SHALL follow OWASP Top 10 mitigations as a baseline checklist for both Backend and Web_Portal implementation — this SHALL be included as an explicit reference in the project's security steering documentation.

---

## Technology Stack Reference

| Layer | Component |
|---|---|
| Android | Kotlin, Jetpack Compose, Room, WorkManager, Hilt, Retrofit |
| Backend | Spring Boot (Kotlin) |
| Web portal | React + TypeScript, Dexie.js, Service Worker, react-i18next |
| Database | PostgreSQL |
| Cache | Redis |
| Auth | Keycloak (OIDC/OAuth2) |
| Queue | Postgres-backed job table (`SKIP LOCKED`); Kafka deferred |
| Deployment | Docker Compose (v1); Kubernetes-ready |
| Observability | Prometheus, Grafana, OpenTelemetry, ELK/Loki |
| CI/CD | GitHub Actions or GitLab CI |
| Localization | Android `strings.xml` (en, hi, mr), react-i18next (en, hi, mr), Spring `MessageSource` (en, hi, mr) |
| LLM abstraction | LiteLLM (or equivalent adapter) — user-supplied OpenAI/Gemini/Claude API keys, no platform-funded LLM calls |
| UI design system | Material Design 3 (Android/Compose), matching token system (web) |
