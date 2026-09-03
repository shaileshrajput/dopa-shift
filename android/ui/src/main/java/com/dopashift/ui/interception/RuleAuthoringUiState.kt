package com.dopashift.ui.interception

import com.dopashift.domain.entity.LimitType
import java.util.UUID

/**
 * A lightweight, immutable display model for an existing persisted Rule shown in
 * the Rule_Authoring_Screen's existing-rule list (Requirement 3.2).
 *
 * Carries only what the screen needs to render and act on a Rule: its [id]
 * (for edit/pause/delete calls), the [appPackageName], the current
 * [dailyLimitMinutes], and the derived enabled/paused state ([enabled],
 * [pausedForToday]) evaluated against the current local day.
 */
data class ExistingRuleDisplay(
    val id: UUID,
    val appPackageName: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
    val pausedForToday: Boolean,
)

/**
 * Immutable UI state for the Rule_Authoring_Screen.
 *
 * Holds the full sorted installed-app list (Requirement 2.1), the current
 * search query, the filtered/displayed subset (Requirement 2.2), the set of
 * selected package names (0..N — Requirement 2.4), an empty-result flag
 * (search matched nothing — Requirement 2.3), and a loading flag.
 *
 * Task 17.1 adds the per-app daily-limit picker and validation state
 * (Requirements 2.5, 2.6, 2.7) plus the save outcome state (Requirements 2.9,
 * 2.10):
 * - [perAppLimits] maps a selected package to its last *valid* daily limit in
 *   whole minutes (defaulting to 30 on selection — Requirement 2.5). An invalid
 *   entry never overwrites the retained valid value (Requirement 2.7).
 * - [limitError] flags that the most recent limit entry was rejected; the
 *   Compose screen (18.2) resolves the localized "1–480 whole minutes" range
 *   message. [limitErrorPackage] identifies which app's field to annotate.
 * - [requiredError] flags a blocked save because no app has a valid limit
 *   (Requirement 2.10); the screen supplies the localized required message.
 * - [saveSuccess] indicates a successful persist (Requirement 2.9).
 *
 * Task 18.1 adds existing-rule management state:
 * - [existingRules] holds the user's persisted Rules (limit + enabled/paused
 *   state) for display (Requirement 3.2).
 * - [editLimitError] / [editErrorRuleId] mirror the create-path [limitError]
 *   pattern for a rejected edit of an existing Rule's limit (Requirement 3.4).
 * - [pendingDeleteRuleId] tracks a delete awaiting confirmation (Requirement 3.6;
 *   the dialog is task 18.2's concern).
 *
 * This is a plain immutable data class with no Android dependencies so the
 * list/search/selection/validation behavior can be unit- and property-tested
 * directly.
 */
data class RuleAuthoringUiState(
    /** The complete, case-insensitively sorted list of selectable apps. */
    val allApps: List<InstalledApp> = emptyList(),
    /** The current search query text entered by the user. */
    val searchQuery: String = "",
    /**
     * The apps currently displayed. Equals [allApps] when the query is blank,
     * otherwise the case-insensitive label-contains matches of [searchQuery].
     */
    val displayedApps: List<InstalledApp> = emptyList(),
    /** Package names the user has selected to monitor (0..N). */
    val selectedPackages: Set<String> = emptySet(),
    /**
     * The last *valid* daily limit (whole minutes, 1..480) per selected package.
     * Seeded to [DEFAULT_LIMIT_MINUTES] when an app is selected (Requirement 2.5)
     * and dropped when the app is deselected. A rejected entry never mutates the
     * retained value (Requirement 2.7).
     */
    val perAppLimits: Map<String, Int> = emptyMap(),
    /**
     * The Limit_Type currently selected for the Rule being authored. Defaults to
     * [LimitType.Once] for a newly opened screen (Requirements 1.1, 1.2). When
     * [LimitType.Once], the daily-limit picker ([perAppLimits]) applies and
     * [repetitiveIntervalMinutes] is null; when [LimitType.Repetitive], the
     * interval picker applies (Requirements 1.3, 1.4).
     */
    val limitType: LimitType = LimitType.Once,
    /**
     * The last *valid* Repetitive_Interval in whole minutes (1..120) — the value
     * persisted when [limitType] is [LimitType.Repetitive] (Requirements 1.4,
     * 1.5). Null when [limitType] is [LimitType.Once] or no interval has been set
     * yet. A rejected entry never mutates this retained value (Requirement 1.6).
     */
    val repetitiveIntervalMinutes: Int? = null,
    /**
     * True when the most recent Repetitive_Interval entry was rejected as
     * out-of-range or non-whole (Requirement 1.6). The screen (task 9.2) resolves
     * the localized "Interval must be between 1 and 120 whole minutes" message
     * and the prior valid value is retained.
     */
    val intervalError: Boolean = false,
    /** True while the installed-app list is being loaded. */
    val isLoading: Boolean = true,
    /** True when a non-blank search query matched no app labels (Requirement 2.3). */
    val isEmptyResult: Boolean = false,
    /**
     * True when the most recent daily-limit entry was rejected as out-of-range
     * or non-whole (Requirement 2.7). The screen resolves the localized
     * "1–480 whole minutes" range message for [limitErrorPackage].
     */
    val limitError: Boolean = false,
    /** The package whose limit entry was rejected, or null when [limitError] is false. */
    val limitErrorPackage: String? = null,
    /**
     * True when a save was blocked because zero apps are selected or no selected
     * app has a valid daily limit (Requirement 2.10). The screen resolves the
     * localized "at least one app with a valid daily limit is required" message.
     */
    val requiredError: Boolean = false,
    /** True after a successful persist of the confirmed rules (Requirement 2.9). */
    val saveSuccess: Boolean = false,
    /**
     * The user's existing persisted Rules with their current limit and
     * enabled/paused state (Requirement 3.2). Populated by collecting the
     * repository's reactive Flow; re-emitted whenever a Rule is edited, paused,
     * or deleted so the list always reflects persisted state.
     */
    val existingRules: List<ExistingRuleDisplay> = emptyList(),
    /**
     * True when the most recent edit of an existing Rule's daily limit was
     * rejected as out-of-range or non-whole (Requirement 3.4). The prior value
     * is retained (the repo is never called) and the screen resolves the
     * localized range/error indication for [editErrorRuleId].
     */
    val editLimitError: Boolean = false,
    /** The Rule id whose edit was rejected, or null when [editLimitError] is false. */
    val editErrorRuleId: UUID? = null,
    /**
     * The Rule id the user has requested to delete, pending confirmation
     * (Requirement 3.6 — the confirmation dialog itself is task 18.2). Null when
     * no delete is pending. The actual deletion is performed by [confirmDelete].
     */
    val pendingDeleteRuleId: UUID? = null,
) {
    companion object {
        /** Default per-app daily limit in whole minutes (Requirement 2.5). */
        const val DEFAULT_LIMIT_MINUTES: Int = 30

        /** Inclusive lower bound for a valid daily limit in whole minutes (Requirement 2.6). */
        const val MIN_LIMIT_MINUTES: Int = 1

        /** Inclusive upper bound for a valid daily limit in whole minutes (Requirement 2.6). */
        const val MAX_LIMIT_MINUTES: Int = 480

        /** Inclusive lower bound for a valid Repetitive_Interval in whole minutes (Requirement 1.5). */
        const val MIN_INTERVAL_MINUTES: Int = 1

        /** Inclusive upper bound for a valid Repetitive_Interval in whole minutes (Requirement 1.5). */
        const val MAX_INTERVAL_MINUTES: Int = 120
    }
}
