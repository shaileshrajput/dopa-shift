# Requirements Document — DopaShift

## Introduction

DopaShift is a multi-goal life-tracking system spanning an Android app and a web portal (Windows/iOS deferred to a later phase), built cloud-agnostic, offline-first, and horizontally scalable. The system lets a user define life goals (Career Growth, Fitness, Build Business, Clear Exam, etc.), intercepts distracting screen time with a non-bypassable-by-default overlay, recommends educational content dynamically, tracks habits via 30-day roadmaps, and syncs data bidirectionally across devices with field-level conflict resolution. The platform is built for solo/AI-agent-driven development (Kiro) and must therefore ship with explicit, unambiguous specs — no requirement here should require an implementation-time judgment call that changes behavior.

**ASSUMPTIONS requiring confirmation before Kiro implements them (unresolved in source discussion):**
- Multilingual scope: UI-string translation is in scope; auto-translation of user-generated content (task/goal text) is explicitly OUT of scope for this version — stored and displayed in the language the user entered it.
- Backend queue mechanism: Postgres-backed job table (`SKIP LOCKED` pattern) rather than Kafka, per deferred-infrastructure decision.
- Deployment target for v1: Docker Compose, single or few hosts. Kubernetes manifests are out of scope for v1.
- BYO-LLM is used for three purposes: (1) goal/habit-preparation text suggestions, (2) video link suggestions with the actual video sourced via the link the LLM returns, and (3) habit-track content preparation. Video suggestions come directly from the user's own configured LLM (ChatGPT, Gemini, or Claude), not from a platform-side YouTube API call — YouTube Data API is retained only as a fallback when no LLM is configured or the LLM's response contains no parseable video link. See Requirement 3 and Requirement 15.

---

## Requirement 1: Multi-Goal Framework

**User Story:** As a user, I want to define multiple life-goal profiles with associated keywords, tasks, and habit programs, so that I can track distinct areas of my life independently.

### Acceptance Criteria
1. WHEN a user creates a goal profile THEN the system SHALL require a name, a category, and at least one keyword.
2. WHEN a user adds keywords to a goal THEN the system SHALL store them as a list associated with that goal, used later for content recommendation matching.
3. WHEN a user creates a checklist task under a goal THEN the system SHALL associate the task with exactly one goal — this goal-scoped checklist item is a distinct entity from the standalone daily to-do list defined in Requirement 4; the two SHALL NOT be merged into one data model.
4. IF a user has zero active goals THEN the system SHALL block creation of habit tracks or interception rules that depend on a goal reference.
5. WHEN a user deletes a goal THEN the system SHALL prompt for confirmation and cascade-handle or reassign dependent tasks/habit tracks/interception rules explicitly — orphaned references SHALL NOT be permitted.

---

## Requirement 2: Screen-Time Interception Engine

**User Story:** As a user, I want distracting apps/sites to be monitored against a daily time allowance with a full-screen intercept when the allowance is depleted, so that I can enforce my own limits.

### Acceptance Criteria
1. WHEN the Android app is installed THEN the system SHALL request `PACKAGE_USAGE_STATS` and `SYSTEM_ALERT_WINDOW` via the manual Settings grant flow, since neither can be granted silently on Android 6.0+.
2. IF the device is running Android Go edition THEN the system SHALL detect this and inform the user that overlay interception is unavailable on this device class, rather than failing silently.
3. WHEN a designated distraction app/site is in the foreground THEN the system SHALL track elapsed time against the user-configured daily allowance for that app/site.
4. WHEN the daily allowance for an app/site is depleted THEN the system SHALL display a full-screen intercept overlay of type `TYPE_APPLICATION_OVERLAY`.
5. WHEN the intercept overlay is active THEN the system SHALL NOT provide an in-overlay control that dismisses it before a cooldown or alternative action (habit checkbox, video view) is completed.
6. IF the user revokes the overlay or usage-stats permission at the OS level THEN the system SHALL detect the revocation and notify the user that interception is disabled, rather than assume it is still active.
7. WHEN the app targets Android 15+ THEN the system SHALL ensure a visible overlay window exists before starting any background-triggered foreground service, per platform-enforced ordering.
8. THE system SHALL document, in-app, that interception can be bypassed via OS-level permission revocation, force-stop, or OEM battery-management app killers — "non-bypassable" SHALL be scoped as "not bypassable through in-app UI," not as an absolute guarantee.
9. WHEN the intercept overlay is displayed THEN the system SHALL also render the user's pending to-do list (open tasks from Requirement 4) within the overlay, alongside the habit checkbox (Requirement 6) and video suggestion (Requirement 3) — the overlay SHALL surface productive alternatives, not just a blocking message.
10. WHEN the daily allowance for an app/site is depleted THEN the system SHALL, in addition to the overlay, trigger a to-do list reminder notification (per Requirement 5) summarizing the user's pending tasks, so the reminder is visible even if the overlay is later dismissed via OS-level bypass (AC8).
11. WHEN a task is completed from within the intercept overlay's to-do list view THEN the system SHALL sync that completion through the same change-log pipeline as any other to-do item update (Requirement 4, AC4–AC5) — the overlay is a view onto the same `DailyTodoItem` data, not a separate list.

---

## Requirement 3: Dynamic Content Recommendation Pipeline

**User Story:** As a user, I want educational video suggestions related to my active goal's keywords, sourced through my own LLM (ChatGPT, Gemini, or Claude), shown inside the intercept overlay, so that intercepted time redirects toward my goals using an AI provider I control.

### Acceptance Criteria
1. WHEN the intercept overlay is displayed AND the user has a configured LLM provider (Requirement 15) THEN the system SHALL request video suggestions from that LLM, prompted with the active goal's name/keywords, and SHALL request the response include direct video links.
2. WHEN the LLM response contains one or more parseable video links (YouTube URL pattern or equivalent supported platform) THEN the system SHALL extract the link(s), validate the URL resolves to a real, embeddable video (server-side check via YouTube Data API's video-lookup endpoint using the extracted video ID — this is a validation/metadata call, not a search call, and stays within a much smaller quota cost), and display it via embedded playback.
3. IF the LLM response contains no parseable video link, OR the extracted link fails validation (private/deleted/region-blocked video) THEN the system SHALL fall back to a server-side YouTube Data API keyword search (the original pipeline: cached, goal-keyword-based, quota-managed) rather than show an error or blank overlay.
4. IF the user has no LLM provider configured THEN the system SHALL use the YouTube Data API keyword-search pipeline as the sole source, unchanged from the original design.
5. WHEN a recommendation request is made via the YouTube API (validation or fallback search) THEN the system SHALL pass `relevanceLanguage` and `regionCode` derived from the user's stored locale.
6. WHEN a video is selected/displayed inside the overlay THEN the system SHALL play it via embedded playback without leaving the overlay context.
7. THE system SHALL cache LLM-suggested video links per goal-keyword-set for a configurable TTL, to avoid calling the user's LLM (which may itself have rate limits or per-call cost to the user) on every single overlay trigger.
8. THE system SHALL log/display to the user when a shown video came from their LLM vs. the YouTube-search fallback, so provider behavior is transparent, not silent — this is a background/settings-level log (e.g., viewable in an activity history), not an inline interruption; the fallback SHALL happen silently and automatically within the overlay itself, with no user-facing error or interruption at the point of use.

---

## Requirement 4: Daily To-Do List & Cross-Device Task Sync

**User Story:** As a user, I want a daily to-do list — separate from goal-linked checklist items — that I can check off, strike through, and edit, staying in sync between Android and the web portal regardless of which device I used last, so that I can act from anywhere.

### Acceptance Criteria
1. THE system SHALL model the daily to-do list as a distinct entity (`DailyTodoItem`) from the goal-scoped checklist item (Requirement 1, AC3) — a to-do item SHALL NOT require an associated goal and SHALL be creatable independently.
2. THE system SHALL support full CRUD on to-do items: create, edit (text, due date/time), delete, and mark complete/incomplete.
3. WHEN a to-do item is marked complete THEN the UI SHALL render it with a checked checkbox and strikethrough text style, and SHALL retain it in a "completed" view rather than deleting it — the user SHALL be able to un-check it to revert.
4. WHEN a to-do item is created, edited, or completed on any client THEN the system SHALL write the change to the local store immediately, regardless of connectivity.
5. WHEN connectivity is available THEN the system SHALL push queued local changes to the backend as append-only change-log events (`entity_id, field, value, timestamp, device_id, user_id`).
6. WHEN two clients edit different fields of the same to-do item while both are offline THEN the system SHALL merge both edits on sync — field-level merge, not whole-record Last-Write-Wins.
7. WHEN two clients edit the *same field* of the same to-do item while both are offline THEN the system SHALL resolve using a server-assigned logical timestamp (not device wall-clock time) and SHALL flag the losing edit as superseded, retrievable in a conflict history rather than silently discarded.
8. WHEN a client reconnects THEN the system SHALL pull all change-log events since its last known sync point and apply them locally before allowing new local writes to sync.
9. THE goal-scoped checklist item (Requirement 1, AC3) SHALL use the same sync engine and conflict-resolution rules (AC4–AC8) as the daily to-do list — the sync mechanism is shared infrastructure; only the data model and goal-association are different.

---

## Requirement 5: Flexible Reminder Scheduling & Rigorous Notifications

**User Story:** As a user, I want to set reminders on any day, at any time, as one-off or repeating, and optionally conditional, for to-do items, goal checklist items, and habit tracks alike, so that I don't rely on remembering to check the app, and missed activities are actively escalated rather than shown once and forgotten.

### Acceptance Criteria
1. THE system SHALL support setting one or more reminders on any entity type — `DailyTodoItem` (Requirement 4), goal-scoped checklist item (Requirement 1, AC3), and habit-track checkpoint (Requirement 6) — using one shared reminder model, not a per-entity-type reimplementation.
2. THE system SHALL support one-off reminders scheduled for any user-specified date and time, with no restriction on how far in the future.
3. THE system SHALL support repeating reminders with configurable recurrence: daily, specific weekdays (e.g., Mon/Wed/Fri), weekly, monthly, or a custom interval (every N days).
4. THE system SHALL support conditional reminders — a reminder that fires only when a defined condition is met (e.g., "remind me only if this to-do item is still incomplete by 6 PM," "remind me only if today's habit checkbox is unchecked") rather than firing unconditionally at the scheduled time.
5. WHEN a reminder's scheduled time arrives AND (for conditional reminders) its condition evaluates true THEN the system SHALL schedule a local notification on Android via `WorkManager`/`AlarmManager`.
6. WHEN the web portal is open or has an active service worker registration THEN the system SHALL support push notifications for the same scheduled/repeating/conditional reminders.
7. WHEN a notification is generated THEN the system SHALL localize its text using the user's stored locale preference via server-side `MessageSource`.
8. IF a reminder's underlying entity (to-do item, checklist item, habit checkpoint) is edited, completed, or deleted before the trigger fires THEN the system SHALL cancel or reschedule the reminder accordingly, including recomputing the next occurrence for a repeating reminder.
9. WHEN a screen-time allowance is depleted and the intercept overlay triggers (Requirement 2, AC10) THEN the system SHALL send a to-do list reminder notification summarizing pending tasks — distinct from any scheduled reminder in AC5, triggered by the interception event rather than a scheduled time.
10. WHEN a reminder fires AND is not acknowledged (opened/completed/dismissed) within a user-configurable interval THEN the system SHALL re-trigger a follow-up reminder, up to a user-configurable maximum escalation count.
11. WHEN a habit-track day's micro-habit is not checked off by a user-configurable cutoff time THEN the system SHALL send a dedicated escalation reminder distinct from the routine daily reminder.
12. THE system SHALL let the user configure reminder aggressiveness per entity, per goal, or globally (e.g., reminder frequency, escalation count, quiet hours) — reminders SHALL NOT fire during user-configured quiet hours regardless of escalation or condition state.
13. THE system SHALL use exact alarms (`AlarmManager.setExactAndAllowWhileIdle` or equivalent) for reminder and escalation timing, since standard `WorkManager` deferred execution is not guaranteed precise enough for time-exact or escalating reminders.
14. THE recurring/conditional reminder rules SHALL be evaluated client-side against locally stored entity state (offline-capable) and SHALL re-sync/reconcile against the backend once connectivity resumes, consistent with the offline-first requirement (Requirement 8, AC4).

---

## Requirement 6: Automated Habit Roadmap Builder

**User Story:** As a user, I want pre-configured 30-day habit tracks tied to a goal, with the current day's micro-habit shown inside the intercept overlay, so that habit-building is reinforced at the moment of distraction.

### Acceptance Criteria
1. WHEN a user activates a habit track for a goal THEN the system SHALL initialize a 30-day sequence of micro-habit checkpoints tied to that goal.
2. WHEN the intercept overlay is displayed AND an active habit track exists for the current goal THEN the system SHALL render the current day's micro-habit checkbox inside the overlay.
3. WHEN the user checks off the current day's micro-habit THEN the system SHALL record completion with a timestamp and advance the track state.
4. IF a day is missed THEN the system SHALL record it as missed rather than skip silently — habit-track completion history SHALL be queryable for the analytics requirement (Requirement 7).

---

## Requirement 7: Daily Efficiency Auditing & Analytics

**User Story:** As a user, I want a daily efficiency score with historical charts comparing productive vs. distracting time, so that I can see trends over time.

### Acceptance Criteria
1. WHEN a day completes (local midnight, user's timezone) THEN the system SHALL compute an efficiency percentage from tracked productive vs. distracting foreground time.
2. WHEN the user views analytics THEN the system SHALL render historical bar charts and line graphs from locally stored (offline-available) daily scores, falling back to backend-synced history for date ranges not present locally.
3. THE system SHALL compute efficiency scores identically regardless of which client (Android/web) the underlying telemetry originated from.

---

## Requirement 8: Local-First Data Privacy & Sync

**User Story:** As a user, I want raw tracking telemetry to stay local while only top-level metrics and configuration sync to the cloud, so that detailed behavioral data isn't unnecessarily centralized.

### Acceptance Criteria
1. THE system SHALL store raw foreground-app telemetry and interception logs only in local device storage (Room/SQLite on Android, Dexie/IndexedDB on web) — this data SHALL NOT be transmitted to the backend in raw form.
2. WHEN data syncs to the backend THEN the system SHALL transmit only aggregated top-level metrics (daily efficiency score, task/goal/habit state, configuration) — not raw telemetry.
3. THE system SHALL encrypt all data in transit (TLS) and SHALL encrypt sensitive configuration/credential data at rest.
4. THE system SHALL function fully for local task/goal/habit management with zero connectivity, syncing only when connectivity resumes.

---

## Cross-Cutting Requirement 9: Cloud-Agnostic Infrastructure

**User Story:** As the system owner, I want the backend built only on self-hostable, portable components, so that the platform can run on any cloud provider or bare metal without redesign.

### Acceptance Criteria
1. THE system SHALL use PostgreSQL as the primary datastore — no proprietary managed-database APIs (e.g., DynamoDB, Firestore).
2. THE system SHALL use S3-compatible object storage APIs — no direct coupling to a single provider's storage SDK.
3. THE system SHALL use Keycloak for authentication/authorization — no Firebase Auth or Cognito dependency.
4. THE system SHALL be fully containerized (Docker) and deployable via Docker Compose for v1, with a documented path to Kubernetes for later scaling — no compute logic SHALL depend on a specific cloud's serverless runtime.

---

## Cross-Cutting Requirement 10: Generic Multi-Platform API

**User Story:** As a developer (or AI agent) building any client, I want one versioned API with no platform-specific branches, so that Android, web, and future clients consume identical contracts.

### Acceptance Criteria
1. THE system SHALL expose a single versioned REST API (`/v1/...`) defined by an OpenAPI 3 specification, authoritative for all clients.
2. THE API SHALL NOT contain platform-conditional fields or endpoints — platform-specific behavior SHALL be implemented client-side.
3. THE system SHALL authenticate all API requests via OAuth2/OIDC bearer tokens issued by Keycloak.
4. WHEN the API spec changes in a breaking way THEN the system SHALL introduce a new version path rather than mutate the existing one.

---

## Cross-Cutting Requirement 11: Scalability

**User Story:** As the system owner, I want the backend able to scale horizontally as user count grows, without redesigning the architecture.

### Acceptance Criteria
1. THE backend SHALL be stateless at the API layer — session/auth state SHALL live in Keycloak/Redis, not in-process memory, so any instance can serve any request.
2. THE change-log/sync-event table SHALL be indexed by `user_id + timestamp` and designed for append-only writes with periodic archival of old entries.
3. THE video-recommendation cache SHALL be keyed by goal-keyword set, not by user, so cache hit rate improves with user count rather than degrading.
4. THE system SHALL document a defined migration path from Docker Compose to Kubernetes and from a Postgres-backed job queue to Kafka, triggered by measured load thresholds rather than implemented speculatively in v1.

---

## Cross-Cutting Requirement 12: Multilingual Support

**User Story:** As a user, I want the app interface in my preferred language, so that I can use it comfortably regardless of locale.

### Acceptance Criteria
1. THE system SHALL store a `preferredLanguage`/`locale` field on the user profile as part of the core user entity.
2. THE Android app SHALL localize all static UI strings via `res/values-<lang>/strings.xml`.
3. THE web portal SHALL localize all static UI strings via `react-i18next`, switchable without a full page reload.
4. THE backend SHALL localize server-generated text (notifications, error messages) via Spring `MessageSource`/`LocaleResolver`, keyed off the user's stored locale.
5. THE system SHALL NOT auto-translate user-generated content (goal names, task text) — such content SHALL be stored and displayed exactly as entered, per the assumption stated in the Introduction (confirm before implementation).
6. IF a translation string is missing for the user's locale THEN the system SHALL fall back to English rather than rendering a blank or key-name string.

---

## Cross-Cutting Requirement 13: Enterprise/Production-Grade Quality

**User Story:** As the system owner, I want production-grade engineering practices from the start, so that the system is maintainable, secure, and observable as it grows.

### Acceptance Criteria
1. THE backend SHALL have automated tests (unit via JUnit5/Kotest, integration via Testcontainers against real Postgres/Redis) running in CI on every change.
2. THE Android app SHALL have automated tests via Espresso/Compose Test; the web portal via Jest and Playwright.
3. THE system SHALL run dependency vulnerability scanning (OWASP dependency-check or equivalent) in CI.
4. THE system SHALL expose metrics via Prometheus-compatible endpoints and SHALL be dashboarded via Grafana.
5. THE system SHALL implement distributed tracing via OpenTelemetry across API and background-job execution paths.
6. THE system SHALL centralize logs (ELK or Loki stack) rather than rely on per-instance log files.
7. THE system SHALL manage secrets via a dedicated secrets mechanism (Vault or sealed secrets) — no credentials SHALL be committed to source control or stored in plaintext config.

---

## Cross-Cutting Requirement 14: Architecture Pattern Compliance

**User Story:** As the system owner, I want the codebase to follow Clean/Hexagonal Architecture, so that business logic is platform-independent and testable in isolation.

### Acceptance Criteria
1. THE domain layer (goal, task, habit, efficiency-scoring logic) SHALL have no dependency on Spring, Android, or React framework code.
2. THE system SHALL implement the Repository pattern to abstract local-storage vs. API access from domain logic.
3. THE system SHALL implement the Strategy pattern for swappable conflict-resolution and OAuth-provider logic.
4. THE system SHALL implement reactive/observer-based local data propagation (Kotlin Flow on Android, signals/RxJS on web) so UI updates react to local DB changes without polling.
5. THE system SHALL implement the Adapter pattern to wrap each external OAuth provider behind one common interface.

---

## Cross-Cutting Requirement 15: Bring-Your-Own-LLM Integration

**User Story:** As a user, I want to use my own ChatGPT, Gemini, or Claude account/API key for goal suggestions, habit preparation, and video recommendations, so that I control which AI provider processes my data and which provider sources my content suggestions.

### Acceptance Criteria
1. THE system SHALL let a user configure a personal LLM provider (OpenAI/ChatGPT, Google/Gemini, or Anthropic/Claude) by supplying their own API key in account settings.
2. THE system SHALL store the user's LLM API key encrypted at rest (via the secrets-management mechanism defined in Requirement 13) and SHALL NOT log it in plaintext anywhere, including error logs.
3. THE system SHALL route all LLM calls through a provider-agnostic abstraction layer (e.g., LiteLLM or an equivalent internal adapter) so switching providers requires no logic change — this directly reuses the Adapter pattern already required in Requirement 14.
4. WHEN a user requests AI-assisted goal-definition or habit-preparation suggestions (e.g., goal description, keyword ideas, 30-day habit track content) THEN the system SHALL call the user's configured LLM provider with the relevant context and SHALL NOT fall back to a platform-funded LLM without explicit user configuration of one.
5. WHEN the system requests video suggestions from the user's LLM (Requirement 3) THEN it SHALL prompt for direct video links and SHALL handle the case where the provider's underlying model has no live web/browsing capability — some API-only LLM calls (e.g., a plain Claude or GPT completion without a search tool enabled) cannot return real current links and may hallucinate them; the system SHALL therefore always validate any returned link server-side (Requirement 3, AC2) before display, never trust and embed an LLM-provided link unvalidated.
6. IF no LLM provider is configured THEN the system SHALL disable AI-assisted suggestion features gracefully (hidden or clearly labeled as unavailable) and SHALL use the YouTube-search-only video pipeline, rather than error.
7. WHEN an LLM call is made THEN the system SHALL send only the minimum necessary context (e.g., goal name/keywords, not the user's full task history or telemetry) — this is a privacy boundary consistent with Requirement 8's local-first telemetry principle.
8. THE system SHALL let the user test/validate their configured API key (a simple validation call) before saving it, and SHALL surface clear errors for invalid keys, expired keys, or rate-limit responses.
9. THE system SHALL surface to the user, in settings, which of the three supported providers (ChatGPT, Gemini, Claude) have live web-search/browsing capability available under their plan, since this directly affects whether video-link suggestions (AC5) are likely to return valid results versus needing the YouTube-search fallback — this is informational guidance, not a hard restriction on provider choice.

---

## Cross-Cutting Requirement 16: Performance, Memory, and Battery Efficiency

**User Story:** As a user, I want the app to run efficiently in the background without draining battery or consuming excessive memory, so that I'm not deterred from keeping interception and tracking active at all times.

### Acceptance Criteria
1. THE Android background monitoring service SHALL use batched/interval-based `UsageStatsManager` polling (not continuous tight-loop polling) — polling interval SHALL be configurable and SHALL default to a value that balances interception responsiveness against battery drain (starting point: 5–10 second intervals, not sub-second).
2. THE system SHALL request exemption from OS battery optimization (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) only for the specific foreground service required for interception, with a clear user-facing explanation of why — not a blanket exemption request.
3. THE Android app SHALL avoid holding wake locks longer than the minimum required for an active interception check or overlay render.
4. THE system SHALL lazy-load and paginate historical analytics data (Requirement 7) rather than loading full history into memory on screen open.
5. THE local database queries (Room/Dexie) SHALL be indexed on frequently filtered fields (`user_id`, `goal_id`, `date`) to avoid full-table scans on analytics/chart rendering.
6. THE system SHALL profile and set a memory budget for the background service (e.g., target under a defined MB ceiling — exact figure to be set after initial profiling, not guessed here) since Android may kill services that exceed reasonable memory footprints under system pressure.
7. THE web portal SHALL code-split routes/bundles so the initial load (critical for the offline app-shell requirement) stays minimal.

---

## Cross-Cutting Requirement 17: Professional UI/UX Design Standard

**User Story:** As a user, I want the app's visual design and interaction quality to match established, polished habit/goal-tracking apps, so that the product feels credible and pleasant to use daily.

### Acceptance Criteria
1. THE UI SHALL follow a consistent design system (spacing scale, typography scale, color tokens, component library) rather than ad-hoc per-screen styling — Material Design 3 for Android (native fit with Jetpack Compose), a matching design-token system for the web portal.
2. THE system SHALL provide light and dark theme support, consistent with the visual standard set by comparable tracker apps (Habitify, Streaks, Fabulous).
3. THE habit/goal tracking screens SHALL prioritize progress visualization (streaks, completion rings/bars, charts) as first-class UI elements, not secondary text-only displays — matching the visual-feedback pattern common to the referenced apps.
4. THE onboarding flow SHALL be a guided, minimal-friction sequence (goal selection, first habit setup) rather than a blank-state dashboard on first launch.
5. THIS requirement is a design-quality bar, not a literal UI clone — Kiro/implementers SHALL treat the named apps (Habitify, Streaks/Calistree, Fabulous) as a quality and interaction-pattern reference only; no visual assets or copyrighted UI elements from those apps SHALL be reproduced.

---

## Cross-Cutting Requirement 18: Traceability, Logging, and Monitoring

**User Story:** As the system owner, I want end-to-end traceability of user actions and system events across all components, structured audit logging for sensitive operations, and real-time monitoring with alerting, so that issues can be diagnosed quickly, security-relevant changes are permanently recorded, and system health is proactively managed — complementing the infrastructure-level observability already defined in Requirement 13.

### Acceptance Criteria

1. WHEN a user action or system event originates on any client (Android, web portal) THEN the system SHALL generate a unique correlation ID at the point of origin and SHALL propagate it through every downstream service call, background job, and database write associated with that action — enabling full request-path reconstruction from a single ID.
2. THE system SHALL emit structured log entries (JSON format) from all components (Android client, web portal, backend API, background jobs) — each entry SHALL include at minimum: timestamp (ISO 8601, UTC), correlation ID, service name, log level, user ID (when authenticated), and a machine-parseable event type field.
3. WHEN a sensitive operation is performed — including but not limited to: goal deletion (Requirement 1, AC5), LLM API key creation/rotation/deletion (Requirement 15), permission grant/revocation changes in Keycloak, user account deletion, and reminder-aggressiveness configuration changes — THEN the system SHALL write an immutable audit-log entry containing: the correlation ID, authenticated user ID, operation type, timestamp, affected entity ID, and before/after state (where applicable).
4. THE audit-log entries SHALL be stored in an append-only, tamper-evident store (separate table or index from general application logs) and SHALL NOT be deletable via standard application APIs — only a documented administrative retention-policy process SHALL remove expired audit entries.
5. WHEN a log entry or audit entry is written THEN the system SHALL enforce a retention policy: application logs SHALL be retained for a configurable duration (default 30 days), audit logs SHALL be retained for a longer configurable duration (default 1 year) — expired entries SHALL be archived or purged by an automated background job.
6. THE centralized log store (ELK/Loki, per Requirement 13 AC6) SHALL index logs by correlation ID, user ID, service name, and event type — enabling sub-second search across all components by any of these dimensions.
7. THE system SHALL define Grafana dashboard panels (building on Requirement 13 AC4) covering: API request rate and latency percentiles (p50/p95/p99), sync-event queue depth and processing lag, background-job success/failure rates, active-user count, and interception-overlay trigger rate — grouped by service and goal category where applicable.
8. WHEN a monitored metric crosses a defined threshold THEN the system SHALL fire an alert via a configurable channel (webhook, email, or messaging integration) — thresholds SHALL be defined for at minimum: API error rate exceeding 5% over a 5-minute window, sync-queue depth exceeding a configurable ceiling, background-job failure rate exceeding 10% over a 10-minute window, and audit-log write failures (any occurrence).
9. THE Android client SHALL log structured events locally (to the local database, consistent with Requirement 8 AC1's local-first telemetry principle) and SHALL sync only aggregated diagnostic summaries (crash counts, sync-failure counts, overlay-trigger counts) to the backend — raw client-side structured logs SHALL NOT leave the device.
10. THE web portal SHALL emit structured browser-side log events to the backend via a lightweight telemetry endpoint, throttled to avoid excessive network traffic — these events SHALL carry the same correlation ID propagated from the originating user action.
11. WHEN distributed tracing is active (Requirement 13 AC5, OpenTelemetry) THEN the system SHALL embed the correlation ID as a trace attribute/baggage item, so that traces and structured logs can be cross-referenced without separate correlation logic.
12. IF an audit-log write fails (e.g., due to storage unavailability) THEN the system SHALL retry with exponential backoff and SHALL NOT silently drop the entry — if retries are exhausted, the system SHALL fire a critical alert (AC8) and SHALL queue the entry for deferred write upon storage recovery.

---

## Cross-Cutting Requirement 19: User Profile Customization

**User Story:** As a user, I want to upload a profile photo, change my password, and customize the app's accent color, so that my experience feels personalized and my account security is self-managed.

### Acceptance Criteria

#### Profile Photo

1. WHEN a user uploads a profile photo THEN the system SHALL accept only JPEG or PNG format files with a maximum file size of 5 MB.
2. WHEN a valid profile photo is uploaded THEN the system SHALL store it via S3-compatible object storage (consistent with Requirement 9, AC2) and SHALL associate the stored object URL with the user's profile entity.
3. WHEN a user changes their profile photo THEN the system SHALL replace the previous photo object in storage and update the profile reference — the previous object SHALL be deleted to avoid orphaned storage.
4. WHEN a user removes their profile photo THEN the system SHALL delete the stored object and revert the user's displayed avatar to a system-generated default (initials-based or generic silhouette).
5. THE system SHALL display the user's profile photo consistently across Android (Jetpack Compose `AsyncImage` or equivalent) and the web portal — both clients SHALL fetch the photo via the same S3-compatible URL stored on the profile entity.
6. WHILE a user has no profile photo set THEN the system SHALL display a deterministic default avatar derived from the user's display name initials and a stable background color, rather than a blank or broken-image placeholder.

#### Password Change

7. WHEN a user initiates a password change THEN the system SHALL route the request through Keycloak's Account Management API (consistent with Requirement 9, AC3 and Requirement 10, AC3) — the system SHALL NOT implement a custom password-storage or verification mechanism outside Keycloak.
8. WHEN a password change is requested THEN the system SHALL require the user to provide their current password for verification before accepting the new password.
9. WHEN a new password is submitted THEN the system SHALL enforce Keycloak's configured password policy (minimum length, complexity rules, password history) and SHALL surface policy-violation errors to the user in a human-readable, localized form.
10. WHEN a password change succeeds THEN the system SHALL invalidate all existing sessions for that user (across all devices) except the session from which the change was initiated — forcing re-authentication on other clients.
11. IF the current-password verification fails THEN the system SHALL reject the request with a clear error and SHALL NOT reveal whether the failure was due to an incorrect password versus a locked account — consistent with secure authentication error practices.

#### Theme Accent Color

12. THE system SHALL let the user select an accent color from a predefined palette of at least 12 colors OR enter a custom hex color code (#RRGGBB) — this accent color is distinct from the light/dark mode toggle defined in Requirement 17, AC2.
13. WHEN the user selects or enters an accent color THEN the system SHALL persist the choice to the user's profile entity and SHALL sync it across devices via the standard sync mechanism (Requirement 4, AC4–AC5).
14. WHEN a custom hex color is entered THEN the system SHALL validate that the value is a well-formed 6-digit hexadecimal color code and SHALL reject invalid input with an inline validation error.
15. THE Android app SHALL apply the selected accent color using Material Design 3 dynamic color theming (custom `ColorScheme` seed) — the accent color SHALL propagate to primary interactive elements (buttons, toggles, progress indicators, navigation highlights) consistently across all screens.
16. THE web portal SHALL apply the selected accent color via CSS custom properties (design tokens) — the accent color SHALL propagate to primary interactive elements consistently, matching the Android app's visual treatment.
17. WHILE no accent color has been explicitly selected by the user THEN the system SHALL apply a default accent color defined in the design system's base theme — the app SHALL NOT appear un-themed or inconsistent on first use.

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
