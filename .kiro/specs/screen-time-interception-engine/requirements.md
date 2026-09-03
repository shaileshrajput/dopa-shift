# Requirements Document

## Introduction

The Screen-Time Interception Engine is an Android feature that tracks other apps' foreground usage, lets a user select apps to monitor and assign daily time limits, persists and manages those monitoring rules locally, and — when a limit is exceeded — notifies the user and presents an Intercept Screen surfacing the user's goals, habit checkpoint, and today's to-dos. Raw per-sample telemetry stays on-device. This document formalizes the existing concise specification into user stories with EARS-format acceptance criteria without adding or removing capabilities.

## Glossary

- **Android_App**: The DopaShift Android application that hosts the interception feature.
- **Engine**: The background component (foreground service) that samples the current foreground app and accumulates elapsed time per Rule.
- **Rule**: A persisted object linking a Monitored_App to a daily time limit in minutes, plus its state (enabled, paused-for-today, created_at, accumulated time for today).
- **Monitored_App**: Any installed, launchable app the user has selected to observe.
- **Rule_Authoring_Screen**: The UI where the user selects apps, sets limits via a time picker, and views/edits/pauses/deletes Rules.
- **Intercept_Screen**: The UI shown when a Monitored_App exceeds its daily limit; displays goals, a habit checkpoint, and today's to-dos, and lets the user record simple actions.
- **Focus_Extension**: A short, configurable grace period granted after the Intercept_Screen engagement is satisfied, during which the Intercept_Screen does not re-trigger for that app.
- **Suppression_Context**: A safety-critical state (active call, active navigation, ringing alarm/timer, emergency dialer) during which the Intercept_Screen must not appear.
- **Telemetry**: Raw per-sample foreground-package observations recorded by the Engine.
- **Local_Store**: The on-device database (Room) used to persist Rules and aggregated counters.

## Requirements

### Requirement 1: Track Other Apps' Foreground Screen Time

**User Story:** As a user who wants to curb distraction, I want the app to track how long I spend in other apps, so that my daily time limits can be enforced against real usage.

#### Acceptance Criteria

1. WHILE at least one Rule exists AND the usage-access permission and display-over-other-apps permission are granted, THE Engine SHALL sample the current foreground package at a configurable interval with a default of 5 seconds.
2. THE Engine SHALL constrain the configurable sampling interval to a range of 3 seconds to 30 seconds inclusive.
3. IF a requested sampling interval falls outside the range of 3 to 30 seconds, THEN THE Engine SHALL reject the value and retain the previous valid interval.
4. WHEN a sample's foreground package matches a Rule's app package, THE Engine SHALL attribute the elapsed time since the previous sample to that Rule's accumulated foreground time for the current day.
5. THE Engine SHALL persist each Rule's accumulated foreground time to the Local_Store at least once every 60 seconds.
6. IF a persistence write to the Local_Store fails, THEN THE Engine SHALL retain the in-memory accumulated value and retry on the next persistence cycle.
7. IF the usage-access permission is revoked while the Engine is running, THEN THE Engine SHALL stop sampling and notify the user that monitoring has stopped.
8. THE Engine SHALL store raw per-sample Telemetry on-device only.
9. THE Engine SHALL exclude raw per-sample Telemetry from transmission to backend services.

### Requirement 2: App Selection and Time Picker

**User Story:** As a user, I want to choose which apps to monitor and set a daily limit for each, so that I control what gets intercepted.

#### Acceptance Criteria

1. THE Rule_Authoring_Screen SHALL list all installed, launchable apps, displaying each app's icon and label, sorted alphabetically by label.
2. WHEN the user enters one or more characters in the search field, THE Rule_Authoring_Screen SHALL filter the app list to apps whose label contains the entered text using case-insensitive matching.
3. IF the search text matches no app labels, THEN THE Rule_Authoring_Screen SHALL display an empty result state with a message indicating that no apps match the search, while retaining any existing selections.
4. THE Rule_Authoring_Screen SHALL allow the user to select between 0 and the total number of listed apps to monitor.
5. WHERE an app is selected for monitoring, THE Rule_Authoring_Screen SHALL present a time picker for setting a daily limit in whole minutes, pre-populated with a default value of 30 minutes.
6. THE Rule_Authoring_Screen SHALL constrain the daily limit to a minimum of 1 minute and a maximum of 480 minutes.
7. IF the user attempts to set a daily limit below 1 minute, above 480 minutes, or to a non-whole-minute value, THEN THE Rule_Authoring_Screen SHALL reject the entry, retain the last valid limit value, and display a message indicating the accepted range of 1 to 480 whole minutes.
8. THE Rule_Authoring_Screen SHALL exclude system-critical apps from the app list, specifically the Android_App itself, the launcher, the phone/dialer, and system settings.
9. WHEN the user confirms the selection with at least one app selected and a valid daily limit set for each selected app, THE Rule_Authoring_Screen SHALL persist the monitoring rules to local storage and indicate save success to the user.
10. IF the user attempts to confirm the selection with zero apps selected or with any selected app lacking a valid daily limit, THEN THE Rule_Authoring_Screen SHALL block the save, retain the current selections, and display a message indicating that at least one app with a valid daily limit is required.

### Requirement 3: Persist and Remove Rules

**User Story:** As a user, I want my monitoring rules saved and fully manageable, so that I can adjust or remove them whenever my needs change.

#### Acceptance Criteria

1. WHEN a Rule is saved, THE Android_App SHALL persist it in the Local_Store, including app package, daily limit in minutes, enabled/paused state, and created_at timestamp.
2. WHEN the user opens the Rule_Authoring_Screen, THE Rule_Authoring_Screen SHALL display each existing Rule's app package, daily limit, and enabled/paused state.
3. WHEN the user edits the daily limit of an existing Rule to a valid value, THE Android_App SHALL persist the change to the Local_Store within 2 seconds.
4. IF the user edits a Rule's daily limit to an invalid value (below 1 minute, above 480 minutes, or non-whole-minute), THEN THE Rule_Authoring_Screen SHALL reject the change, retain the prior value, and display an error indication.
5. WHEN the user pauses a Rule for the current day, THE Engine SHALL suppress accumulation and interception for that Rule until the next local calendar day boundary (00:00 device local time), after which the Rule SHALL return to its enabled state.
6. WHEN the user deletes a Rule, THE Rule_Authoring_Screen SHALL request confirmation before removing it.
7. WHEN a Rule is deleted, THE Engine SHALL stop accumulating time for that Rule within one sampling interval.
8. THE Android_App SHALL scope every Rule create, read, update, and delete operation to the authenticated user_id, and SHALL NOT allow access to or modification of another user's Rule.
9. IF a Local_Store create, update, or delete operation fails, THEN THE Android_App SHALL preserve the prior persisted state and display an error indication to the user.

### Requirement 4: Background Engine and Interception

**User Story:** As a user, I want the app to intercept me when I exceed a limit and offer productive alternatives, so that I redirect distracted time toward my goals.

#### Acceptance Criteria

1. WHILE at least one enabled Rule exists AND the PACKAGE_USAGE_STATS permission and notification permission are granted, THE Engine SHALL run as a foreground service.
2. WHEN the user initiates the PACKAGE_USAGE_STATS grant flow, THE Android_App SHALL open the Android Settings grant screen for that permission.
3. BEFORE opening the PACKAGE_USAGE_STATS grant screen, THE Android_App SHALL display a rationale explaining why the permission is needed.
4. WHEN a Rule's accumulated time for the current day exceeds its configured daily limit, THE Engine SHALL, within one sampling interval of the breach, post a local notification informing the user that the limit was reached and offering a single action to open the Intercept_Screen for that app.
5. WHEN the user taps the limit-reached notification, THE Android_App SHALL present the Intercept_Screen for the associated app.
6. THE Intercept_Screen SHALL display the user's goals, a habit checkpoint, and today's to-dos.
7. THE Intercept_Screen SHALL allow the user to record simple actions, specifically checking a habit and completing a to-do.
8. WHEN the user records an action on the Intercept_Screen, THE Android_App SHALL write that action to the same local data pipelines used elsewhere in the Android_App.
9. WHEN the user records at least one action on the Intercept_Screen, specifically checking a habit or completing a to-do, THE Engine SHALL grant a Focus_Extension with a default duration of 5 minutes.
10. THE Engine SHALL constrain the configurable Focus_Extension duration to a range of 1 minute to 15 minutes inclusive.
11. WHILE a Focus_Extension is active for an app, THE Engine SHALL suppress re-triggering the Intercept_Screen for that app.
12. THE Engine SHALL cap the number of Focus_Extensions granted per app to 3 per calendar day.
13. IF a Rule's accumulated time exceeds its daily limit after 3 Focus_Extensions have already been granted for that app on the current calendar day, THEN THE Engine SHALL present the Intercept_Screen without granting a further Focus_Extension and SHALL indicate to the user that no additional extensions are available today.
14. IF the PACKAGE_USAGE_STATS permission or notification permission is revoked while the Engine is running, THEN THE Engine SHALL stop the foreground service and SHALL notify the user that monitoring has stopped because a required permission was revoked.

### Requirement 5: Safety and Permissions

**User Story:** As a user, I want the interception to respect device limitations and safety-critical moments, so that the overlay never appears when it could cause harm or fail silently.

#### Acceptance Criteria

1. WHEN the Android_App starts on an Android Go edition on which overlay or monitoring features are unavailable, THE Android_App SHALL, within 5 seconds, inform the user that these features are unavailable and disable interception.
2. WHILE a Suppression_Context is active — specifically an active call, active navigation, a ringing alarm or timer, or the emergency dialer — THE Engine SHALL prevent the Intercept_Screen from appearing.
3. IF a Suppression_Context is active when a limit breach triggers interception, THEN THE Engine SHALL defer presentation of the Intercept_Screen for up to 300 seconds.
4. WHEN the Suppression_Context ends within the deferral window AND the Monitored_App remains in the foreground, THE Engine SHALL present the deferred Intercept_Screen within 2 seconds.
5. IF the Suppression_Context ends AND the Monitored_App is no longer in the foreground, THEN THE Engine SHALL discard the deferred Intercept_Screen without presenting it.
6. IF the 300-second deferral window elapses while the Suppression_Context is still active, THEN THE Engine SHALL discard the deferred Intercept_Screen and re-evaluate on the next monitoring cycle.

### Requirement 6: Storage and Privacy

**User Story:** As a privacy-conscious user, I want my raw usage data kept on my device, so that only aggregated data ever leaves it and only with my consent.

#### Acceptance Criteria

1. WHEN Rules or aggregated counters are written, THE Android_App SHALL persist them in the Local_Store on-device, and IF the write fails THEN THE Android_App SHALL preserve the prior persisted state and surface an error indication.
2. THE Engine SHALL retain raw per-sample Telemetry on-device only.
3. THE Engine SHALL exclude raw per-sample Telemetry records from remote transmission.
4. WHERE the user has given explicit consent, THE Android_App SHALL sync only aggregated counts or diagnostic summaries, and SHALL NEVER sync raw per-sample Telemetry.
5. WHEN the user revokes sync consent, THE Android_App SHALL immediately cancel any in-flight or queued sync of aggregated data and cease further syncing until consent is re-granted.

---

## Open Questions

| ID | Question | Impact | Recommended Answer |
|---|---|---|---|
| OQ-1 | Is a 3-per-day Focus_Extension cap (Requirement 4, AC12) the right ceiling, or should it be user-configurable like the extension duration itself? | Too low frustrates legitimate work use of a tracked app; too high defeats the daily allowance entirely. | Fixed at 3/day for v1, non-configurable, to prevent the setting itself from being tuned into ineffectiveness; revisit after usage data. |
| OQ-2 | How is an app classified "productive" vs. "distracting" for the Efficiency_Score (parent Requirement 7, AC1)? The parent spec defines the scoring formula but never the classification input, and this engine is the natural source of that signal. | The Efficiency_Score is unimplementable as currently specified without this engine's rule data. | Every app with an active interception Rule counts as distracting; all other tracked foreground time counts as productive. This needs its own acceptance criterion in the parent document, not just an assumption here. |
| OQ-3 | Should Suppression_Context detection (Requirement 5, AC2) extend to third-party video-call apps (e.g., a video meeting), which `TelephonyManager` call state does not cover? | Missing this would let the overlay interrupt a video meeting. | Out of scope for v1 — `TelephonyManager` and system navigation/alarm signals only; flag for a future spec once a reliable cross-app signal is identified. |

---

## Traceability Matrix

| This spec | Parent requirement(s) | Relationship |
|---|---|---|
| Requirement 1 | 2 (AC1–AC2, AC8), 16 (AC2) | Extends — parent has no rule-authoring UI requirement (C-5) |
| Requirement 2 | 2 (AC12), 8 (AC1), 16 (AC1) | **Extends and overrides** Req 2 AC12 (C-2) |
| Requirement 3 | 2 (AC4–AC5, AC7, AC10), 5 (AC10) | **Extends and overrides** Req 2 AC5 (C-1) |
| Requirement 4 | 2 (AC9, AC11), 3 (AC1, AC6), 6 (AC3) | Refines |
| Requirement 5 | 2 (AC6, AC8), 16 (AC3) | **Extends** — new recovery and safety criteria (C-3, C-4) |
| Requirement 6 | 16 (AC3, AC6, AC8) | Refines |
| STI-7 (Localization) | 4 (AC10), 18 (AC1–AC3, AC9) | Refines — carried over from prior traceability; localization of Intercept_Screen and notification strings (en, hi, mr) applies per platform localization rules |
| STI-8 (Sync) | 21 (AC1, AC10, AC13) | Refines — carried over from prior traceability; aggregated-counter sync (Requirement 6, AC4) follows offline-first change-log conventions |
