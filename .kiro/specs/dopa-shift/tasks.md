# Implementation Plan: DopaShift Multi-Goal Life-Tracking Platform

## Overview

This implementation plan breaks DopaShift into incremental, buildable tasks starting with shared infrastructure (database schema, domain layer, sync engine), then platform-specific implementations (Backend API, Android app, Web portal), and finally cross-cutting concerns (observability, security, testing). Each task builds on previous tasks, ensuring no orphaned code. The backend uses Kotlin/Spring Boot, Android uses Kotlin/Jetpack Compose, and the web portal uses React/TypeScript.

## Tasks

- [x] 1. Project scaffolding and infrastructure setup
  - [x] 1.1 Initialize Spring Boot backend project with Kotlin, Gradle, and multi-module structure (domain, application, infrastructure layers per Clean Architecture)
    - Create Gradle multi-module project: `domain`, `application`, `infrastructure`, `api`
    - Configure Kotlin compiler, Spring Boot plugin, and dependency management
    - Ensure `domain` module has zero Spring/framework dependencies
    - _Requirements: 14.1, 14.2, 14.6_

  - [x] 1.2 Create Docker Compose configuration for local development environment
    - PostgreSQL 14+, Redis, Keycloak 22+, MinIO, OpenTelemetry Collector, Prometheus, Grafana, Loki
    - Externalize all configuration via environment variables
    - Include nginx load-balancer for API instances
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

  - [x] 1.3 Create PostgreSQL database schema migration files (Flyway or Liquibase)
    - Implement all tables from the design: users, goals, keywords, goal_checklist_items, daily_todo_items, habit_tracks, habit_checkpoints, reminders, change_log, conflict_history, audit_log, efficiency_scores, interception_rules, video_cache, llm_configurations, job_queue
    - Include all indexes, constraints, and foreign keys
    - _Requirements: 9.1, 11.2_

  - [x] 1.4 Configure Keycloak realm, client, and roles for DopaShift
    - Create realm with OIDC/OAuth2 client configuration
    - Define RBAC roles (user, admin)
    - Configure password policies
    - _Requirements: 9.3, 10.3, 22.1, 22.10_

  - [x] 1.5 Initialize Android project with Kotlin, Jetpack Compose, Hilt, Room, and multi-module structure
    - Create modules: `domain`, `data`, `ui`, `sync`, `interception`
    - Configure Hilt dependency injection
    - Ensure domain module has no Android SDK imports
    - _Requirements: 14.1, 14.6_

  - [x] 1.6 Initialize React + TypeScript web portal project with Vite, Dexie.js, and react-i18next
    - Configure project structure with domain/data/ui layer separation
    - Set up Dexie.js schema matching local data model
    - Configure code-splitting for route-based lazy loading
    - _Requirements: 14.1, 16.7_

- [x] 2. Checkpoint - Ensure project scaffolding compiles and Docker Compose starts
  - Ensure all tests pass, ask the user if questions arise.

- [x] 3. Domain layer — core entities, repository interfaces, and use cases
  - [x] 3.1 Implement domain entities (GoalProfile, DailyTodoItem, GoalChecklistItem, HabitTrack, HabitCheckpoint, EfficiencyScore, Reminder, ChangeLogEntry, AuditLogEntry)
    - Define data classes in the domain module with all validation rules
    - Enforce constraints: goal name 1-100 chars, keyword max 20 per goal, keyword 1-50 chars, todo text max 500 chars, habit checkpoint desc 1-200 chars
    - _Requirements: 1.1, 1.2, 4.2, 4.12, 6.1_

  - [x] 3.2 Implement repository port interfaces (GoalRepository, DailyTodoRepository, HabitTrackRepository, ChangeLogRepository, AuditLogRepository, ReminderRepository, EfficiencyScoreRepository)
    - Define suspend functions with proper return types
    - Ensure no framework imports in the domain module
    - _Requirements: 14.1, 14.2_

  - [x] 3.3 Implement ConflictResolutionStrategy interface and strategies (LastWriteWins, DeleteWins)
    - Define Strategy interface with resolve method
    - Implement LastWriteWinsStrategy (server-assigned logical timestamp)
    - Implement DeleteWinsStrategy (delete authoritative over edit)
    - Define MergeResult sealed class
    - _Requirements: 4.6, 4.7, 4.11, 14.3_

  - [x] 3.4 Write property test for goal name uniqueness enforcement
    - **Property 1: Goal Name Uniqueness Enforcement**
    - **Validates: Requirements 1.7**

  - [x] 3.5 Write property test for field-level merge preserving non-conflicting edits
    - **Property 3: Field-Level Merge Preserves Non-Conflicting Edits**
    - **Validates: Requirements 4.6**

  - [x] 3.6 Write property test for same-field conflict resolution determinism
    - **Property 4: Same-Field Conflict Resolution Is Deterministic**
    - **Validates: Requirements 4.7**

  - [x] 3.7 Write property test for delete wins over edit
    - **Property 5: Delete Wins Over Edit**
    - **Validates: Requirements 4.11**

  - [x] 3.8 Implement LlmProviderService and LlmAdapter interfaces with adapter factory
    - Define provider-agnostic adapter interface (LlmAdapter)
    - Implement LlmAdapterFactory with provider type dispatch
    - _Requirements: 15.3, 14.3, 14.5_

  - [x] 3.9 Implement domain use cases for goal management (CreateGoal, UpdateGoal, DeleteGoal with dependency resolution)
    - CreateGoal: validate name uniqueness, enforce keyword limits
    - DeleteGoal: resolve dependents (DELETE_ALL or REASSIGN)
    - Block Habit_Track/interception creation if zero active goals
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 1.7_

  - [x] 3.10 Write property test for goal deletion leaves no orphans
    - **Property 2: Goal Deletion Leaves No Orphans**
    - **Validates: Requirements 1.5, 1.6**

  - [x] 3.11 Implement domain use cases for daily todo management (CreateTodo, UpdateTodo, DeleteTodo, MarkComplete)
    - Enforce 100 items per user per day limit
    - Enforce 500-character text limit
    - Model DailyTodoItem as distinct from GoalChecklistItem
    - _Requirements: 4.1, 4.2, 4.3, 4.12_

  - [x] 3.12 Write property test for daily todo item count limit
    - **Property 17: Daily Todo Item Count Limit**
    - **Validates: Requirements 4.2**

  - [x] 3.13 Write property test for keyword count and length validation
    - **Property 18: Keyword Count and Length Validation**
    - **Validates: Requirements 1.2**

  - [x] 3.14 Implement domain use cases for habit track management (ActivateHabitTrack, MarkCheckpoint, AdvanceDay, RecordMissedDay)
    - Initialize 30-day checkpoint sequence
    - Advance current-day pointer on completion
    - Record MISSED status for unchecked days
    - _Requirements: 6.1, 6.3, 6.4, 6.5, 6.6_

  - [x] 3.15 Write property test for habit track day advancement correctness
    - **Property 13: Habit Track Day Advancement Correctness**
    - **Validates: Requirements 6.3**

  - [x] 3.16 Write property test for missed habit day recording
    - **Property 14: Missed Habit Day Recording**
    - **Validates: Requirements 6.4**

  - [x] 3.17 Implement efficiency score computation logic
    - Formula: (productive_seconds / total_tracked_seconds) * 100, rounded to nearest integer
    - Return null/unavailable if total tracked < 60 seconds
    - Ensure deterministic computation regardless of client
    - _Requirements: 7.1, 7.2, 7.5_

  - [x] 3.18 Write property test for efficiency score computation consistency
    - **Property 10: Efficiency Score Computation Consistency**
    - **Validates: Requirements 7.5**

  - [x] 3.19 Implement reminder domain logic (scheduling, recurrence evaluation, condition evaluation, escalation, quiet-hours suppression)
    - Support all recurrence types: daily, specific weekdays, weekly, monthly, custom interval
    - Conditional reminders: fire only when entity is incomplete
    - Escalation: configurable interval and max count
    - Quiet-hours suppression with delivery at window end
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.8, 5.11, 5.13_

  - [x] 3.20 Write property test for reminder quiet-hours suppression and delivery
    - **Property 11: Reminder Quiet-Hours Suppression and Delivery**
    - **Validates: Requirements 5.13**

  - [x] 3.21 Write property test for conditional reminder fires only when condition holds
    - **Property 12: Conditional Reminder Fires Only When Condition Holds**
    - **Validates: Requirements 5.4, 5.8**

- [x] 4. Checkpoint - Ensure domain layer compiles in isolation with all property tests passing
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Sync engine implementation
  - [x] 5.1 Implement Change_Log server-side processor (append entries, assign server logical timestamps, detect conflicts)
    - Append-only writes with server timestamp assignment
    - Detect same-entity same-field conflicts
    - Apply resolution strategy (LWW or DeleteWins)
    - Store superseded entries in conflict_history
    - _Requirements: 4.5, 4.6, 4.7, 4.11, 11.2_

  - [x] 5.2 Implement sync push endpoint (POST /v1/sync/push) — accept client change_log events, process conflicts, return resolution results
    - Accept batch of change_log entries from client
    - Assign server timestamps
    - Return resolved conflicts and server timestamps
    - _Requirements: 4.5, 4.6, 4.7_

  - [x] 5.3 Implement sync pull endpoint (GET /v1/sync/pull) — return change_log events since client's last sync timestamp
    - Query by user_id and timestamp > since
    - Return events ordered by timestamp
    - 30-second timeout with graceful handling
    - _Requirements: 4.8_

  - [x] 5.4 Implement conflict history endpoint (GET /v1/sync/conflicts) — return superseded entries retained for 90+ days
    - Query conflict_history by user_id and since timestamp
    - _Requirements: 4.7_

  - [x] 5.5 Write property test for change log serialization round-trip
    - **Property 15: Change Log Serialization Round-Trip**
    - **Validates: Requirements 4.4, 4.5**

  - [x] 5.6 Write property test for sync push retry with exponential backoff
    - **Property 19: Sync Push Retry with Exponential Backoff**
    - **Validates: Requirements 4.10**

  - [x] 5.7 Implement Android sync engine client (local Change_Log queue, push/pull with exponential backoff, field-level merge application)
    - Persist changes to local Room Change_Log table
    - Push queued events when online
    - Pull and apply remote events on reconnect
    - Retry with exponential backoff (1s, 2s, 4s, 8s, 16s) up to 5 attempts
    - Display sync-pending indicator on failure
    - _Requirements: 4.4, 4.5, 4.8, 4.10_

  - [x] 5.8 Implement Web portal sync engine client (Dexie Change_Log queue, push/pull, field-level merge)
    - Mirror Android sync engine behavior using Dexie/IndexedDB
    - Same retry and backoff logic
    - Service Worker integration for background sync
    - _Requirements: 4.4, 4.5, 4.8, 4.10_

- [x] 6. Checkpoint - Ensure sync engine integration tests pass with multi-client conflict scenarios
  - Ensure all tests pass, ask the user if questions arise.

- [x] 7. Backend API — REST controllers and application services
  - [x] 7.1 Implement authentication/authorization filter (OAuth2 bearer token validation via Keycloak, user_id scoping)
    - Validate bearer tokens against Keycloak
    - Extract user_id from token claims
    - Reject missing/malformed/expired tokens
    - Implement RBAC middleware
    - _Requirements: 10.3, 10.4, 22.1, 22.10, 22.11_

  - [x] 7.2 Implement Goals REST controller (CRUD endpoints with dependency resolution on delete)
    - POST /v1/goals — create with name uniqueness validation
    - GET /v1/goals — list user's goals
    - GET /v1/goals/{id} — detail with keywords and stats
    - PUT /v1/goals/{id} — update
    - DELETE /v1/goals/{id} — with DELETE_ALL or REASSIGN action
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 1.7_

  - [x] 7.3 Implement Goal Checklist REST controller (CRUD for goal-scoped checklist items)
    - POST /v1/goals/{id}/checklist
    - GET /v1/goals/{id}/checklist
    - PUT /v1/goals/{goalId}/checklist/{itemId}
    - DELETE /v1/goals/{goalId}/checklist/{itemId}
    - _Requirements: 1.3_

  - [x] 7.4 Implement Daily Todo REST controller (CRUD with 100-item limit enforcement)
    - POST /v1/todos — enforce 100/user/day limit
    - GET /v1/todos?date={YYYY-MM-DD}
    - PUT /v1/todos/{id}
    - DELETE /v1/todos/{id}
    - GET /v1/todos/pending
    - _Requirements: 4.1, 4.2, 4.3, 4.12_

  - [x] 7.5 Implement Habit Track REST controller (activate, list, checkpoint management)
    - POST /v1/habits — activate track for a goal
    - GET /v1/habits?goalId={id}
    - GET /v1/habits/{id} — with all checkpoints
    - PUT /v1/habits/{id}/checkpoints/{day} — mark completed/missed
    - GET /v1/habits/current — for overlay
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [x] 7.6 Implement Reminders REST controller (CRUD with recurrence and condition support)
    - POST /v1/reminders
    - GET /v1/reminders
    - PUT /v1/reminders/{id}
    - DELETE /v1/reminders/{id}
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x] 7.7 Implement Interception Rules REST controller (CRUD for app/site tracking rules)
    - POST /v1/interception/rules
    - GET /v1/interception/rules
    - PUT /v1/interception/rules/{id}
    - DELETE /v1/interception/rules/{id}
    - Validate daily allowance 1-480 minutes
    - _Requirements: 2.3_

  - [x] 7.8 Implement User Profile REST controller (profile CRUD, photo upload/delete, password change via Keycloak)
    - GET/PUT /v1/profile — locale, accent color, timezone, quiet hours
    - POST/DELETE /v1/profile/photo — S3-compatible storage, 5MB max, JPEG/PNG only
    - POST /v1/profile/password — route through Keycloak Account Management API
    - _Requirements: 19.1, 19.2, 19.3, 19.4, 19.5, 19.6, 19.7, 19.8, 19.9, 19.10, 19.11, 19.12, 19.13, 19.14, 19.15, 19.16, 19.17_

  - [x] 7.9 Implement LLM Configuration REST controller (configure, validate, rotate API keys)
    - POST /v1/settings/llm — configure provider + encrypted key storage
    - GET /v1/settings/llm — return config with masked key
    - PUT /v1/settings/llm — rotate key
    - DELETE /v1/settings/llm — remove config
    - POST /v1/settings/llm/validate — test call with 10s timeout
    - _Requirements: 15.1, 15.2, 15.8, 22.3, 22.12_

  - [x] 7.10 Implement Content/Video Recommendation endpoint with LLM + YouTube fallback pipeline
    - GET /v1/content/video?goalId={id}
    - Call user's LLM with goal name + keywords
    - Extract YouTube URLs, validate via YouTube Data API
    - Fallback to YouTube keyword search if no valid links
    - Cache results per keyword-set hash (24h TTL)
    - Pass relevanceLanguage/regionCode from user locale
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7, 3.8_

  - [x] 7.11 Write property test for video recommendation fallback chain
    - **Property 8: Video Recommendation Fallback Chain**
    - **Validates: Requirements 3.2, 3.3**

  - [x] 7.12 Write property test for LLM context privacy boundary
    - **Property 9: LLM Context Privacy Boundary**
    - **Validates: Requirements 15.7**

  - [x] 7.13 Implement Analytics/Dashboard REST controller (efficiency scores, dashboard summary, activity feed)
    - GET /v1/analytics/efficiency?from={date}&to={date}
    - POST /v1/analytics/efficiency — submit computed daily score
    - GET /v1/dashboard/summary — aggregated data
    - GET /v1/dashboard/activity?page={n}&size=20 — paginated feed
    - _Requirements: 7.1, 7.3, 20.1, 20.2, 20.3, 20.6, 20.10, 20.12_

  - [x] 7.14 Implement Auth endpoints (token exchange, refresh, logout via Keycloak proxy)
    - POST /v1/auth/token
    - POST /v1/auth/refresh
    - POST /v1/auth/logout
    - _Requirements: 10.3, 22.1_

  - [x] 7.15 Implement rate limiting middleware for all API endpoints
    - Stricter limits on authentication endpoints
    - Standard limits on data endpoints
    - _Requirements: 22.8_

  - [x] 7.16 Implement input validation and sanitization layer (server-side validation regardless of client)
    - Parameterized queries via JPA/ORM
    - Input length/format validation on all user-supplied fields
    - _Requirements: 22.7_

  - [x] 7.17 Implement standardized error response format with correlation IDs
    - JSON error format: code, message, details, correlationId, timestamp
    - Generate and propagate correlation IDs
    - _Requirements: 18.1, 18.2_

- [x] 8. Checkpoint - Ensure all backend API integration tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 9. Audit logging and traceability
  - [x] 9.1 Implement audit log service (append-only, hash-chained entries for tamper evidence)
    - Hash chain: each entry includes hash of previous entry
    - Record before/after state for sensitive operations
    - Correlation ID propagation from request origin
    - _Requirements: 18.3, 18.4, 18.13_

  - [x] 9.2 Write property test for audit log hash chain integrity
    - **Property 16: Audit Log Hash Chain Integrity**
    - **Validates: Requirements 18.13**

  - [x] 9.3 Implement structured logging (JSON format) with correlation ID, trace ID, redaction of sensitive data
    - JSON log entries with: timestamp, correlationId, service, level, userId, eventType
    - Redact/mask LLM API keys, auth tokens, PII at logging-framework level
    - Embed correlation ID as OpenTelemetry trace attribute
    - _Requirements: 18.2, 18.6, 18.11, 22.4_

  - [x] 9.4 Implement audit log retention policy background job (30-day app logs, 1-year audit logs)
    - Automated background job for archival/purge
    - Retry with exponential backoff on write failures
    - Critical alert on exhausted retries
    - _Requirements: 18.5, 18.12_

- [x] 10. Background job processor
  - [x] 10.1 Implement PostgreSQL-backed job queue processor (SKIP LOCKED pattern)
    - Poll for pending jobs with SKIP LOCKED
    - Process jobs with retry logic (max 5 attempts)
    - Support job types: audit archival, change_log archival, video cache eviction, missed habit detection
    - _Requirements: 11.2, 11.3_

  - [x] 10.2 Implement change_log archival job (archive entries older than 90 days when exceeding 1M rows)
    - Triggered weekly or when table exceeds 1M rows
    - Archive/purge entries older than 90 days
    - _Requirements: 11.2_

  - [x] 10.3 Implement missed habit day detection job (run at midnight per user timezone, mark unchecked days as MISSED)
    - Evaluate all active habit tracks at user's configured midnight
    - Record MISSED status with date for unchecked checkpoints
    - _Requirements: 6.4_

- [x] 11. Android app — data layer and repository implementations
  - [x] 11.1 Implement Room database with all entity tables and DAOs
    - Define Room entities matching local data model (goals, todos, checklists, habits, checkpoints, reminders, change_log, efficiency_scores, interception_rules, telemetry_events, sync_state)
    - Implement DAOs with indexed queries on user_id, goal_id, date fields
    - _Requirements: 8.1, 16.5_

  - [x] 11.2 Implement repository implementations (Room-backed) conforming to domain port interfaces
    - GoalDao → GoalRepository implementation
    - DailyTodoDao → DailyTodoRepository implementation
    - All repos implement domain interfaces from the domain module
    - _Requirements: 14.2_

  - [x] 11.3 Implement Retrofit API client for all backend REST endpoints
    - Configure OAuth2 bearer token interceptor
    - Handle token refresh automatically
    - _Requirements: 10.3_

  - [x] 11.4 Implement local telemetry storage (foreground-app events stored locally only, never transmitted)
    - Store raw telemetry in Room telemetry_events table
    - Ensure no code path transmits raw telemetry to backend
    - Sync only aggregated efficiency scores
    - _Requirements: 8.1, 8.2_

- [x] 12. Android app — UI layer
  - [x] 12.1 Implement DopaShift Kinetic design system in Compose (theme, colors, typography, spacing)
    - Color palette: Productive Blue #0066FF, Momentum Teal #2DD4BF, Focus Indigo #6366F1
    - Typography: Hanken Grotesk (headlines), Inter (body), JetBrains Mono (labels)
    - 8px spacing rhythm, M3 tonal elevation
    - Light/dark theme support with user accent color via dynamic ColorScheme
    - _Requirements: 17.1, 17.2, 17.6, 19.15_

  - [x] 12.2 Implement Dashboard screen (Today's Status, efficiency score, goal cards grid, todo quick-actions, activity feed)
    - Today's Status: pending todos count, active habits status, goal progress, efficiency score
    - Efficiency trend indicator (improving/declining/stable)
    - Quick-action: mark todo complete, check off habit from dashboard
    - Activity feed with pagination (20 items per page)
    - Optimistic UI updates within 200ms
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.5, 20.6, 20.7, 20.8, 20.9, 20.10, 20.11, 20.13, 20.14, 20.15_

  - [x] 12.3 Implement Goals & Habits screen (goal list, goal detail with checklist, habit tracks)
    - Goal cards with progress ring, streak, category icon
    - Goal detail: keywords, checklist items, active habit tracks
    - Create/edit/delete goals with dependency confirmation dialog
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.6, 1.7_

  - [x] 12.4 Implement Daily Tasks screen (todo list with CRUD, strikethrough on complete, inline editing)
    - List todos for selected day
    - Create/edit/delete with 500-char validation
    - Mark complete with checkbox and strikethrough
    - Un-check to revert completion
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 12.5 Implement Habit Roadmap screen (30-day track visualization, checkpoint completion)
    - Display 30-day sequence with status per day (pending/completed/missed)
    - Mark current day's checkpoint as complete
    - Show earliest-start-date track in overlay, others accessible here
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [x] 12.6 Implement Analytics & Audit screen (efficiency score charts, bar/line graphs, date range selection)
    - Historical bar charts and line graphs (default last 30 days)
    - Lazy-load paginated data (max 50 records per page)
    - "No data" indicator for days with <1 minute tracked
    - Offline-capable with locally cached scores
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 16.4_

  - [x] 12.7 Implement Notifications/Reminders screen (create, edit, delete reminders with recurrence UI)
    - Create reminders for any entity type (todo, checklist, habit checkpoint)
    - Configure recurrence: daily, weekdays, weekly, monthly, custom interval
    - Conditional reminder option
    - Escalation interval and max count configuration
    - Quiet hours configuration
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.11, 5.13_

  - [x] 12.8 Implement Settings screen (profile, LLM config, locale, accent color, permissions info)
    - Profile photo upload (JPEG/PNG, 5MB max)
    - Password change (via Keycloak)
    - Accent color picker (12+ predefined + custom hex)
    - Locale selection (en, hi, mr)
    - LLM provider configuration with validation
    - Interception bypass informational notice
    - _Requirements: 15.1, 15.8, 15.9, 19.1, 19.7, 19.8, 19.12, 19.13, 19.14, 2.8, 12.1_

  - [x] 12.9 Implement bottom navigation (Home, Goals, Analytics, Settings)
    - Bottom navigation bar following wireframe pattern
    - _Requirements: 17.8_

  - [x] 12.10 Implement onboarding flow (max 5 screens, goal selection, first habit setup)
    - Guide user through first goal creation and habit setup
    - Completable in under 2 minutes
    - _Requirements: 17.4_

  - [x] 12.11 Implement reactive data propagation with Kotlin Flow (UI subscribes to Room changes, updates within 2 seconds)
    - ViewModels expose Kotlin Flow from Room DAOs
    - UI recomposes on data changes without polling
    - _Requirements: 14.4_

- [x] 13. Android app — screen-time interception engine
  - [x] 13.1 Implement foreground service for UsageStatsManager polling (configurable 3-30s interval, default 5s)
    - Request PACKAGE_USAGE_STATS and SYSTEM_ALERT_WINDOW permissions
    - Detect Android Go edition and show unavailability message
    - Batched/interval-based polling (not tight loop)
    - _Requirements: 2.1, 2.2, 2.3, 16.1_

  - [x] 13.2 Implement daily allowance tracking and depletion detection
    - Accumulate foreground seconds per tracked app per day
    - Trigger overlay within 2 seconds of allowance depletion
    - Reset accumulated time at midnight in user's timezone
    - _Requirements: 2.3, 2.4, 2.12_

  - [x] 13.3 Write property test for daily allowance depletion triggers overlay
    - **Property 6: Daily Allowance Depletion Triggers Overlay**
    - **Validates: Requirements 2.4**

  - [x] 13.4 Write property test for midnight allowance reset
    - **Property 20: Midnight Allowance Reset**
    - **Validates: Requirements 2.12**

  - [x] 13.5 Implement Intercept_Overlay (TYPE_APPLICATION_OVERLAY) with pending todos, habit checkbox, video suggestion
    - Full-screen overlay rendering pending DailyTodoItems
    - Current day's habit track checkbox
    - Video recommendation (embedded playback)
    - 30-second minimum cooldown + alternative action before dismissal
    - Glassmorphism effects per design system
    - _Requirements: 2.4, 2.5, 2.9, 3.1, 3.6, 6.2, 17.7_

  - [x] 13.6 Write property test for overlay minimum engagement before dismissal
    - **Property 7: Overlay Minimum Engagement Before Dismissal**
    - **Validates: Requirements 2.5**

  - [x] 13.7 Implement permission revocation detection (detect within 5 seconds, notify user)
    - Check overlay and usage-stats permissions on app foreground
    - Notify user if interception is disabled
    - Handle Android 15+ visible-overlay-before-foreground-service requirement
    - _Requirements: 2.6, 2.7_

  - [x] 13.8 Implement todo completion from overlay (same Change_Log pipeline as normal todo updates)
    - Mark DailyTodoItem complete from within overlay
    - Sync via standard Change_Log
    - Trigger todo reminder notification on overlay display
    - _Requirements: 2.10, 2.11_

  - [x] 13.9 Implement battery and memory efficiency constraints for background service
    - Request battery optimization exemption with user-facing explanation
    - Release wake locks within 500ms of check completion
    - Keep heap ≤ 64MB for up to 20 tracked apps
    - Target ≤ 3% battery per hour
    - _Requirements: 16.2, 16.3, 16.6, 16.8_

- [x] 14. Android app — reminder engine
  - [x] 14.1 Implement client-side reminder evaluation engine with injectable clock (offline-capable)
    - Evaluate reminders against local entity state
    - Injectable clock for testability
    - Support all recurrence types
    - Condition evaluation (entity incomplete check)
    - Quiet-hours suppression with delivery at window end
    - Escalation logic with configurable interval and max count
    - _Requirements: 5.5, 5.11, 5.13, 5.14, 5.15, 5.16_

  - [x] 14.2 Implement AlarmManager exact alarms for reminder and escalation timing
    - Use setExactAndAllowWhileIdle for precise timing
    - Schedule escalation alarms on non-acknowledgment
    - Handle device offline/power-off scenarios (deliver on wake)
    - _Requirements: 5.14, 5.16_

  - [x] 14.3 Implement interception-triggered todo reminder notification (top 5 pending tasks)
    - Triggered by allowance depletion event
    - Summarize up to 5 highest-priority pending tasks
    - Distinct from scheduled reminders
    - _Requirements: 5.10_

  - [x] 14.4 Implement habit track escalation reminder (unchecked micro-habit before cutoff time)
    - Fire dedicated escalation if habit not checked by cutoff (default: 1 hour before quiet hours)
    - Distinct from routine daily reminder
    - _Requirements: 5.12_

- [x] 15. Checkpoint - Ensure Android app builds and core flows work on emulator
  - Ensure all tests pass, ask the user if questions arise.

- [x] 16. Web portal — data layer and state management
  - [x] 16.1 Implement Dexie.js database schema and repository implementations
    - Define Dexie tables: goals, dailyTodos, habitTracks, changeLog, syncState
    - Implement repository functions matching domain interfaces
    - Index on userId, dayDate, goalId for fast queries
    - _Requirements: 8.1, 16.5_

  - [x] 16.2 Implement reactive state management (signals/RxJS) for local data propagation
    - UI subscribes to observable data streams from Dexie
    - Updates within 2 seconds without polling
    - _Requirements: 14.4_

  - [x] 16.3 Implement Service Worker for push notifications and background sync
    - Register service worker for push notification delivery
    - Background sync for queued change_log events
    - _Requirements: 5.6_

  - [x] 16.4 Implement fetch-based API client with OAuth2 bearer token management
    - Token storage and refresh logic
    - Bearer token header on all requests
    - _Requirements: 10.3_

- [x] 17. Web portal — UI layer
  - [x] 17.1 Implement DopaShift Kinetic design system as CSS custom properties / design tokens
    - Color tokens, typography scale, 8px spacing rhythm
    - Light/dark theme with accent color via CSS custom properties
    - Glassmorphism effects for overlay and cards
    - _Requirements: 17.1, 17.2, 17.6, 17.7, 19.16_

  - [x] 17.2 Implement Dashboard page (summary cards, efficiency score, goal grid, todo quick-actions, activity feed)
    - Mirror Android dashboard functionality
    - Modular grid layout (2 columns tablet, 3-4 desktop)
    - Quick-add input for todos
    - Optimistic UI updates
    - Paginated activity feed
    - Offline-capable with Dexie data
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.5, 20.6, 20.7, 20.8, 20.9, 20.10, 20.11, 20.13, 20.14, 20.15_

  - [x] 17.3 Implement Goals & Habits page (goal management, checklist, habit tracks)
    - Full goal CRUD with dependency resolution dialogs
    - Checklist management per goal
    - Habit track visualization
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.6, 1.7, 6.1, 6.3, 6.5_

  - [x] 17.4 Implement Daily Tasks page (todo list CRUD, completion, date selection)
    - Same functionality as Android daily tasks
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 17.5 Implement Analytics page (charts, efficiency scores, date range)
    - Bar/line charts with lazy-loaded paginated data
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 16.4_

  - [x] 17.6 Implement Notifications page (reminder management)
    - Create/edit/delete reminders with recurrence configuration
    - _Requirements: 5.1, 5.2, 5.3, 5.4_

  - [x] 17.7 Implement Settings page (profile, LLM config, locale, accent color)
    - Profile photo, password change, accent color, locale, LLM settings
    - _Requirements: 15.1, 19.1, 19.7, 19.12, 19.13, 12.1_

  - [x] 17.8 Implement navigation drawer (persistent on desktop/web)
    - Navigation drawer following wireframe pattern
    - _Requirements: 17.8_

  - [x] 17.9 Implement onboarding flow for web portal
    - Max 5 screens, goal setup, completable in under 2 minutes
    - _Requirements: 17.4_

- [x] 18. Web portal — internationalization
  - [x] 18.1 Implement react-i18next with locale files for en, hi, mr
    - Static UI string translation for all three locales
    - Switchable without page reload
    - Fallback to English for missing translations
    - _Requirements: 12.2, 12.3, 12.5, 12.6, 12.7_

- [x] 19. Backend — internationalization and localization
  - [x] 19.1 Implement Spring MessageSource for server-generated text (notifications, error messages) in en, hi, mr
    - Localized notification text
    - Localized error messages
    - Fallback to English
    - _Requirements: 12.4, 12.6, 5.7_

- [x] 20. Android — internationalization
  - [x] 20.1 Implement Android string resources for en, hi, mr locales
    - res/values/strings.xml, res/values-hi/strings.xml, res/values-mr/strings.xml
    - All static UI strings localized
    - _Requirements: 12.2, 12.6_

- [x] 21. Checkpoint - Ensure web portal builds and Android/web UI renders correctly
  - Ensure all tests pass, ask the user if questions arise.

- [x] 22. Observability and monitoring
  - [x] 22.1 Implement Prometheus metric endpoints (request rate, error rate, latency p50/p95/p99, JVM utilization)
    - Spring Boot Actuator with Micrometer/Prometheus
    - Custom metrics: sync-queue depth, overlay trigger rate, job success/failure rates
    - _Requirements: 13.4, 18.7_

  - [x] 22.2 Implement OpenTelemetry distributed tracing (trace IDs propagated across API requests and background jobs)
    - Configure OpenTelemetry SDK for Spring Boot
    - Propagate trace IDs to background jobs
    - Embed correlation ID as trace attribute
    - _Requirements: 13.5, 18.11_

  - [x] 22.3 Create Grafana dashboards (API metrics, sync-queue depth, job rates, active users, overlay triggers)
    - Dashboard panels per design specification
    - _Requirements: 18.7_

  - [x] 22.4 Implement alerting rules (API error rate >5%, sync-queue >1000, job failure >10%, audit write failures)
    - Configurable alert channels (webhook, email, messaging)
    - _Requirements: 18.8_

  - [x] 22.5 Configure centralized logging (Loki/ELK) with indexing by correlation ID, user ID, service name, event type
    - Sub-second search across all components
    - 30-day retention for application logs
    - _Requirements: 13.6, 18.6_

- [x] 23. Security hardening
  - [x] 23.1 Implement secrets management integration (Vault or sealed secrets for credentials, LLM API keys)
    - AES-256 encryption for stored LLM API keys
    - No credentials in source control or plaintext config
    - _Requirements: 13.7, 15.2, 22.3_

  - [x] 23.2 Implement CI secret scanning and SAST (Semgrep or equivalent)
    - Secret pattern detection in source files
    - Static analysis for Kotlin/Spring and TypeScript/React
    - Fail pipeline on detection
    - _Requirements: 13.8, 22.5, 22.6_

  - [x] 23.3 Implement dependency vulnerability scanning (OWASP Dependency-Check) in CI
    - Fail pipeline on Critical/High (CVSS ≥ 7.0) findings
    - _Requirements: 13.3, 22.5_

  - [x] 23.4 Implement log redaction/masking at logging-framework level (API keys, tokens, PII)
    - Framework-level redaction, not per-call
    - _Requirements: 22.4_

  - [x] 23.5 Configure TLS 1.2+ for all connections (client-to-backend, backend-to-backend)
    - _Requirements: 8.3, 22.2_

- [x] 24. CI/CD pipeline
  - [x] 24.1 Configure CI pipeline (lint, SAST, secret scan, unit tests, integration tests, coverage gates, dependency scan, contract tests)
    - 80% line coverage gate for backend
    - 70% line coverage gate for clients
    - Contract tests validate OpenAPI spec
    - _Requirements: 13.1, 13.2, 21.11, 21.13_

  - [x] 24.2 Configure release pipeline (E2E Playwright tests, Android instrumented tests, build artifacts)
    - Playwright for web E2E including offline scenarios
    - Android Espresso/Compose Test suite
    - _Requirements: 21.11_

- [x] 25. OpenAPI specification and contract tests
  - [x] 25.1 Create OpenAPI 3 specification document for all /v1/ endpoints
    - Single source of truth for all API contracts
    - Document versioning policy (6-month support for previous versions)
    - _Requirements: 10.1, 10.2, 10.5_

  - [x] 25.2 Implement automated contract tests validating API responses against OpenAPI spec
    - Fail CI if any endpoint response diverges from spec
    - _Requirements: 21.8_

- [x] 26. Checkpoint - Ensure full CI pipeline passes green
  - Ensure all tests pass, ask the user if questions arise.

- [x] 27. Integration testing suites
  - [x] 27.1 Implement sync engine deterministic test suite (multi-client offline edit, reconnect in varying orders, assert merge outcomes)
    - Simulate 2+ clients editing offline
    - Same-field and different-field conflict scenarios
    - Reconnect in varying orders
    - Assert field-level merge correctness
    - _Requirements: 21.6_

  - [x] 27.2 Implement reminder engine test suite with injectable/fake clock (multi-day recurrence, escalation, quiet-hours)
    - Fast-forward simulated time
    - Validate all recurrence types
    - Test escalation logic
    - Test quiet-hours suppression and delivery at window end
    - _Requirements: 21.7_

  - [x] 27.3 Implement BYO-LLM integration test suite (mocked responses: valid, invalid key, rate-limit, no video link, fallback path)
    - Cover all failure modes
    - Validate YouTube-search fallback activation
    - _Requirements: 21.9_

  - [x] 27.4 Implement screen-time interception test suite (permission revocation, Android Go detection, overlay trigger consistency)
    - Emulator configurations per supported API levels
    - _Requirements: 21.10_

- [x] 28. Final checkpoint - Ensure all tests pass and full system integration works
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The domain module (task 3.x) must compile in isolation without any framework dependencies
- Raw telemetry never leaves the device — only aggregated metrics sync
- The sync engine is the highest-risk component; its test suite (27.1) is critical
- Android interception requires real device/emulator testing for permission flows
- All LLM calls go through the user's own provider — no platform-funded LLM

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.5", "1.6"] },
    { "id": 1, "tasks": ["1.3", "1.4"] },
    { "id": 2, "tasks": ["3.1", "3.2", "3.8"] },
    { "id": 3, "tasks": ["3.3", "3.9", "3.11", "3.14", "3.17", "3.19"] },
    { "id": 4, "tasks": ["3.4", "3.5", "3.6", "3.7", "3.10", "3.12", "3.13", "3.15", "3.16", "3.18", "3.20", "3.21"] },
    { "id": 5, "tasks": ["5.1", "5.7", "5.8", "11.1"] },
    { "id": 6, "tasks": ["5.2", "5.3", "5.4", "5.5", "5.6", "11.2", "11.3", "11.4", "16.1", "16.2", "16.3", "16.4"] },
    { "id": 7, "tasks": ["7.1", "7.17"] },
    { "id": 8, "tasks": ["7.2", "7.3", "7.4", "7.5", "7.6", "7.7", "7.8", "7.9", "7.10", "7.13", "7.14", "7.15", "7.16"] },
    { "id": 9, "tasks": ["7.11", "7.12", "9.1", "9.3", "10.1"] },
    { "id": 10, "tasks": ["9.2", "9.4", "10.2", "10.3"] },
    { "id": 11, "tasks": ["12.1", "12.11", "17.1", "18.1", "19.1", "20.1"] },
    { "id": 12, "tasks": ["12.2", "12.3", "12.4", "12.5", "12.6", "12.7", "12.8", "12.9", "12.10", "17.2", "17.3", "17.4", "17.5", "17.6", "17.7", "17.8", "17.9"] },
    { "id": 13, "tasks": ["13.1", "13.2", "14.1"] },
    { "id": 14, "tasks": ["13.3", "13.4", "13.5", "13.6", "13.7", "13.8", "13.9", "14.2", "14.3", "14.4"] },
    { "id": 15, "tasks": ["22.1", "22.2", "22.5", "23.1", "23.4", "23.5", "25.1"] },
    { "id": 16, "tasks": ["22.3", "22.4", "23.2", "23.3", "25.2"] },
    { "id": 17, "tasks": ["24.1", "24.2"] },
    { "id": 18, "tasks": ["27.1", "27.2", "27.3", "27.4"] }
  ]
}
```
