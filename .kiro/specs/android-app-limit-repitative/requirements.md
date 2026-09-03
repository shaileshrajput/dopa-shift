# Requirements Document: Repetitive App-Limit Interception

## Introduction

This feature extends the DopaShift Android Screen-Time Interception Engine with a **repetitive limit type**. Today a monitoring Rule enforces a single daily cumulative allowance (`Once`); this feature adds a `Repetitive` limit type that interrupts the user every N minutes of continued foreground usage of a monitored app. It formalizes the rule-authoring UI changes for choosing the limit type and interval, the repetitive tracking loop, the pausing of time accumulation while the intercept overlay is visible, the counter reset on resumption, the overlay action routing ("Continue to app" / "Switch to DopaShift"), and the local-only audit logging of intercept actions.

This document refines and extends the existing `screen-time-interception-engine` spec (which defines the `Once` behavior, foreground sampling, permissions, suppression contexts, and overlay). It does not restate that spec's `Once`-limit behavior except where the `Repetitive` path diverges. All new behavior stays on-device: raw telemetry and individual intercept-interaction records never leave the device.

## Glossary

- **Android_App**: The DopaShift Android application that hosts the interception feature.
- **Engine**: The background foreground service that samples the active foreground app and evaluates elapsed time against active Rules.
- **Rule**: A persisted object linking a Monitored_App to an enforcement threshold, execution state, and limit type.
- **Limit_Type**: The enforcement pattern for a Rule, strictly one of `Once` (single daily cumulative threshold) or `Repetitive` (recurring periodic cycles).
- **Repetitive_Interval**: The configured duration in whole minutes after which an interception triggers repeatedly during continued app usage (for example, every 1, 5, or 30 minutes).
- **Session_Usage**: The foreground time accumulated for a Monitored_App within the current repetitive-interval cycle.
- **Monitoring_State**: The operational state of the Engine for a specific Rule, one of `ACTIVE`, `PAUSED_FOR_OVERLAY`, or `SUPPRESSED`.
- **Monitored_App**: Any installed, launchable application the user has selected for tracking.
- **Rule_Authoring_Screen**: The UI where the user selects apps, configures limit types and intervals, and manages Rule state.
- **Intercept_Screen**: The system overlay presented when a limit condition is breached.
- **Suppression_Context**: A safety-critical state (active call, active navigation, ringing alarm or timer, emergency dialer) during which the Intercept_Screen must not appear.
- **Local_Store**: The on-device database (Room) used to persist Rules, counters, and aggregated audit entries.

## Requirements

### Requirement 1: Limit-Type Configuration and Rule Authoring

**User Story:** As a user configuring screen-time boundaries, I want to choose between a one-time daily limit and a repetitive interval, so that I can control whether an app is restricted once per day or interrupted repeatedly across an ongoing session.

#### Acceptance Criteria

1. WHEN the user adds or edits a Rule on the Rule_Authoring_Screen, THE Rule_Authoring_Screen SHALL display a mandatory Limit_Type selector offering exactly two mutually exclusive options, `Once` and `Repetitive`.
2. WHEN the Rule_Authoring_Screen opens for a new Rule, THE Rule_Authoring_Screen SHALL pre-select `Once` by default.
3. WHILE `Once` is selected, THE Rule_Authoring_Screen SHALL present the existing single daily cumulative threshold input constrained to a whole number of minutes between 1 and 480 inclusive.
4. WHEN `Repetitive` is selected, THE Rule_Authoring_Screen SHALL display an interval picker for setting a recurring interval in whole minutes.
5. THE Rule_Authoring_Screen SHALL constrain the Repetitive_Interval to a minimum of 1 minute and a maximum of 120 minutes inclusive.
6. IF the user enters a Repetitive_Interval below 1 minute, above 120 minutes, or a non-whole-minute value, THEN THE Rule_Authoring_Screen SHALL reject the entry, retain the last valid value, and display a message indicating the accepted range of 1 to 120 whole minutes.
7. WHEN the user confirms and saves a Rule, THE Android_App SHALL persist the Limit_Type, the Repetitive_Interval (WHERE the Limit_Type is `Repetitive`), and the initial tracking counters to the Local_Store scoped to the authenticated user_id.
8. IF the Local_Store write fails on save, THEN THE Android_App SHALL preserve the prior persisted state and surface an error indication to the user.

### Requirement 2: Repetitive Tracking, Overlay Pausing, and Interception

**User Story:** As a user with a repetitive limit configured, I want the Engine to interrupt me every N minutes of use, freeze the timer while the overlay is showing, and only resume counting after I dismiss it, so that time spent interacting with the overlay is not counted against my app limit.

#### Acceptance Criteria

1. WHILE at least one Rule is active AND the PACKAGE_USAGE_STATS and SYSTEM_ALERT_WINDOW permissions are granted, THE Engine SHALL sample the foreground application at a configurable interval between 3 and 30 seconds inclusive, defaulting to 5 seconds.
2. WHILE a Monitored_App governed by a `Repetitive` Rule is in the foreground AND that Rule's Monitoring_State is `ACTIVE`, THE Engine SHALL add the elapsed sample duration to the Rule's Session_Usage counter.
3. WHEN a `Repetitive` Rule's Session_Usage equals or exceeds its configured Repetitive_Interval, THE Engine SHALL present the Intercept_Screen within one sampling interval.
4. BEFORE presenting the Intercept_Screen, THE Engine SHALL verify that no Suppression_Context is active, and IF a Suppression_Context is active THEN THE Engine SHALL defer presentation for up to 300 seconds.
5. WHEN the Engine presents the Intercept_Screen for a `Repetitive` Rule, THE Engine SHALL transition that Rule's Monitoring_State to `PAUSED_FOR_OVERLAY` within one sampling interval.
6. WHILE a Rule's Monitoring_State is `PAUSED_FOR_OVERLAY`, THE Engine SHALL NOT add any elapsed time to that Rule's Session_Usage counter or to any daily counter.
7. WHEN the Intercept_Screen is dismissed AND the Monitored_App returns to the foreground, THE Engine SHALL transition the Rule's Monitoring_State back to `ACTIVE`, reset the Session_Usage counter to 0, and resume accumulating foreground usage toward the Repetitive_Interval.

### Requirement 3: Intercept-Screen Actions and Overlay Routing

**User Story:** As a user facing the intercept overlay, I want clear options to either keep using the app or switch into DopaShift, with the overlay closing cleanly and the Engine resuming the correct state.

#### Acceptance Criteria

1. THE Intercept_Screen SHALL display exactly two primary action controls, labeled "Continue to app" and "Switch to DopaShift".
2. WHEN the user taps "Switch to DopaShift", THE Android_App SHALL dismiss and remove the Intercept_Screen overlay, launch the DopaShift main activity in the foreground, reset the Rule's Session_Usage to 0, and leave the Rule's Monitoring_State as `ACTIVE`.
3. WHEN the user taps "Continue to app", THE Android_App SHALL dismiss and remove the Intercept_Screen overlay, return focus to the Monitored_App, reset the Rule's Session_Usage to 0, and transition the Rule's Monitoring_State back to `ACTIVE` to begin the next interval cycle.
4. WHEN the user executes either overlay action, THE Android_App SHALL append an aggregated audit entry to the Local_Store containing a timestamp, the target app package name, and the action type (`CONTINUE` or `SWITCH_TO_DOPASHIFT`).
5. THE Android_App SHALL NOT transmit individual intercept-interaction records off the device.
6. THE Intercept_Screen action-control labels SHALL be localized in English, Hindi, and Marathi, falling back to English when a translation is missing.

---

## Open Questions

| ID | Question | Impact | Recommended Answer |
|---|---|---|---|
| OQ-1 | Should the Repetitive_Interval upper bound be 120 minutes, or align with the `Once` daily-limit ceiling of 480 minutes? | Too low prevents long-cycle repetitive nudges; matching 480 blurs the distinction from a daily limit. | Cap at 120 minutes for v1 — beyond that a `Once` daily limit is the better tool. Revisit if users request longer repetitive cycles. |
| OQ-2 | When a Suppression_Context defers a `Repetitive` interception past the point where the app leaves the foreground, is the elapsed Session_Usage preserved or reset? | Preserving risks an immediate re-trigger on return; resetting may under-count. | Reset Session_Usage to 0 on foreground loss during a deferred interception (consistent with the resume-and-reset rule in Requirement 2.7); flag for confirmation. |
| OQ-3 | Can a single app have both a `Once` and a `Repetitive` Rule active simultaneously? | Overlapping enforcement could double-intercept the same session. | One active Rule per app package for v1; the Limit_Type selector edits the single Rule rather than creating a second one. |

---

## Traceability Matrix

| This spec | Parent requirement(s) | Related spec (`screen-time-interception-engine`) | Relationship |
|---|---|---|---|
| Requirement 1 (limit-type + interval authoring) | Requirement 2 (AC1, AC2) | Req 2 (AC5–AC7, AC9, AC10), Req 3 (AC1) | **Extends** — adds the `Repetitive` Limit_Type and interval picker to rule authoring |
| Requirement 2 (repetitive tracking + overlay pausing) | Requirement 2 (AC1–AC3), Requirement 16 (AC2) | Req 1 (AC1), Req 4 (AC4), Req 5 (AC2, AC3) | **Extends** — new per-interval accumulation, `PAUSED_FOR_OVERLAY` state, and counter reset |
| Requirement 3 (overlay actions + audit) | Requirement 2 (AC3), Requirement 8 (AC1), Requirement 12 (AC1) | Req 4 (AC5–AC8), Req 6 (AC2, AC3) | **Refines** — explicit two-action routing and local-only audit logging |
