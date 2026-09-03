package com.dopashift.ui.interception

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.entity.LimitType
import com.dopashift.domain.repository.InterceptionRuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Rule_Authoring_Screen.
 *
 * Task 16.2 covers the installed-app list, case-insensitive search filtering,
 * empty-result handling, and 0..N app selection (Requirements 2.1–2.4, 2.8).
 *
 * Task 17.1 (this task) adds the per-app daily-limit picker, validation, and
 * save:
 * - Selecting an app seeds its limit to [RuleAuthoringUiState.DEFAULT_LIMIT_MINUTES]
 *   (30 whole minutes — Requirement 2.5); deselecting drops it.
 * - [setDailyLimit] accepts only whole minutes in 1..480; out-of-range or
 *   non-whole values are rejected, the last valid value is retained, and a
 *   range-error flag is surfaced for the screen to resolve the localized
 *   "1–480 whole minutes" message (Requirements 2.6, 2.7).
 * - [save] requires ≥1 selected app AND every selected app to have a valid
 *   limit; otherwise the save is blocked, selections/limits are retained, and a
 *   required-message flag is surfaced (Requirement 2.10). On success each
 *   confirmed Rule is persisted via [InterceptionRuleRepository] and
 *   [RuleAuthoringUiState.saveSuccess] is set (Requirement 2.9).
 *
 * The validation and save-eligibility logic is exposed as the pure, testable
 * [isValidLimit] and [canSave] functions (companion) so property tests (17.2,
 * 17.3) can exercise accept/reject-and-retain and save eligibility without
 * Android. The search-filter logic remains in [filterByQuery].
 *
 * Task 18.1 (this task) adds existing-rule management:
 * - The user's existing Rules are observed via
 *   [InterceptionRuleRepository.observeForUser] and projected to
 *   [ExistingRuleDisplay] (with enabled/paused state derived against the current
 *   local day) into [RuleAuthoringUiState.existingRules] (Requirement 3.2).
 * - [editRuleLimit] persists a valid limit change via
 *   [InterceptionRuleRepository.updateDailyLimit]; an invalid value is rejected,
 *   the prior value is retained (the repo is never called), and an edit error is
 *   surfaced (Requirements 3.3, 3.4).
 * - [pauseRuleForToday] pauses a Rule for the current local day via
 *   [InterceptionRuleRepository.pauseForToday] (Requirement 3.5).
 * - [deleteRule] removes a Rule via [InterceptionRuleRepository.delete]; the
 *   confirmation dialog is task 18.2's concern, so a [requestDelete] /
 *   [confirmDelete] / [cancelDelete] trio is exposed for the screen to gate the
 *   destructive call (Requirement 3.7).
 */
@HiltViewModel
class RuleAuthoringViewModel @Inject constructor(
    private val installedAppsProvider: InstalledAppsProvider,
    private val interceptionRuleRepository: InterceptionRuleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RuleAuthoringUiState())
    val uiState: StateFlow<RuleAuthoringUiState> = _uiState.asStateFlow()

    // TODO: Replace with actual user ID from auth/session layer.
    // Mirrors the placeholder used by the other :ui ViewModels (Reminders,
    // DailyTasks, Goals, Dashboard, ...) until the auth/session source lands.
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    init {
        loadInstalledApps()
        observeExistingRules()
    }

    /**
     * Collects the authenticated user's existing Rules and projects them to
     * [ExistingRuleDisplay] for the screen (Requirement 3.2). The observed Flow
     * re-emits on every persisted change (edit/pause/delete), so the list is
     * always in sync with the Local_Store. The enabled/paused state is derived
     * against the current local day via [InterceptionRule.isPausedOn].
     */
    private fun observeExistingRules() {
        viewModelScope.launch {
            interceptionRuleRepository.observeForUser(userId).collect { rules ->
                val today = LocalDate.now()
                val display = rules.map { it.toDisplay(today) }
                _uiState.update { it.copy(existingRules = display) }
            }
        }
    }

    /**
     * Loads the sorted, exclusion-filtered installed-app list off the main
     * thread and seeds the displayed list. Re-applies the current search query
     * so selections and any active filter survive a reload.
     */
    fun loadInstalledApps() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.Default) {
                installedAppsProvider.getInstalledApps()
            }
            _uiState.update { state ->
                val query = state.searchQuery
                val displayed = filterByQuery(apps, query)
                state.copy(
                    allApps = apps,
                    displayedApps = displayed,
                    isEmptyResult = query.isNotBlank() && displayed.isEmpty(),
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Updates the search query and recomputes the displayed list.
     *
     * A blank query shows the full list. A non-blank query filters to
     * case-insensitive label-contains matches (Requirement 2.2). When nothing
     * matches, [RuleAuthoringUiState.isEmptyResult] is set (Requirement 2.3);
     * the current selections are never touched here so they are retained across
     * filtering and empty-result states.
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val displayed = filterByQuery(state.allApps, query)
            state.copy(
                searchQuery = query,
                displayedApps = displayed,
                isEmptyResult = query.isNotBlank() && displayed.isEmpty(),
            )
        }
    }

    /**
     * Toggles selection of a single app by package name. Selecting an app seeds
     * its daily limit to the default (30 whole minutes — Requirement 2.5);
     * deselecting drops the retained limit. Selecting/deselecting never mutates
     * the app list or search query, so selections persist across filtering and
     * empty-result states (Requirements 2.3, 2.4).
     */
    fun toggleAppSelection(packageName: String) {
        _uiState.update { state ->
            if (packageName in state.selectedPackages) {
                state.copy(
                    selectedPackages = state.selectedPackages - packageName,
                    perAppLimits = state.perAppLimits - packageName,
                )
            } else {
                state.copy(
                    selectedPackages = state.selectedPackages + packageName,
                    perAppLimits = state.perAppLimits + (packageName to RuleAuthoringUiState.DEFAULT_LIMIT_MINUTES),
                )
            }
        }
    }

    /**
     * Replaces the current selection set outright. Accepts 0..N packages
     * (Requirement 2.4). Newly selected packages are seeded with the default
     * limit (Requirement 2.5); limits for packages no longer selected are
     * dropped. Does not affect the app list, search query, or empty-result state.
     */
    fun setSelection(packageNames: Set<String>) {
        val next = packageNames.toSet()
        _uiState.update { state ->
            val limits = next.associateWith { pkg ->
                state.perAppLimits[pkg] ?: RuleAuthoringUiState.DEFAULT_LIMIT_MINUTES
            }
            state.copy(selectedPackages = next, perAppLimits = limits)
        }
    }

    /**
     * Sets the daily limit (in whole minutes) for a selected app.
     *
     * Accepts only whole minutes in 1..480 (Requirements 2.6, 2.7). A valid
     * value replaces the retained limit and clears any prior limit error. An
     * out-of-range value is rejected: the last valid value is retained and a
     * range-error flag is raised for [packageName] so the screen can show the
     * localized "1–480 whole minutes" message. A no-op is applied when
     * [packageName] is not currently selected.
     */
    fun setDailyLimit(packageName: String, minutes: Int) {
        _uiState.update { state ->
            if (packageName !in state.selectedPackages) return@update state
            if (isValidLimit(minutes)) {
                state.copy(
                    perAppLimits = state.perAppLimits + (packageName to minutes),
                    limitError = false,
                    limitErrorPackage = null,
                )
            } else {
                // Retain the last valid value; only surface the error.
                state.copy(
                    limitError = true,
                    limitErrorPackage = packageName,
                )
            }
        }
    }

    /**
     * Parses a raw time-picker string and delegates to [setDailyLimit]. A
     * non-integer / non-whole-minute entry is rejected the same way an
     * out-of-range value is (Requirement 2.7): the retained limit is preserved
     * and the range error is surfaced.
     */
    fun setDailyLimit(packageName: String, rawMinutes: String) {
        val parsed = rawMinutes.trim().toIntOrNull()
        if (parsed == null) {
            _uiState.update { state ->
                if (packageName !in state.selectedPackages) state
                else state.copy(limitError = true, limitErrorPackage = packageName)
            }
        } else {
            setDailyLimit(packageName, parsed)
        }
    }

    /**
     * Clears a surfaced limit range error once the screen has shown it.
     */
    fun clearLimitError() {
        _uiState.update { it.copy(limitError = false, limitErrorPackage = null) }
    }

    // --- Limit-type + repetitive-interval authoring (task 9.1) --------------

    /**
     * Selects the Limit_Type for the Rule being authored (Requirements 1.1, 1.2).
     *
     * Selecting [LimitType.Once] clears the retained interval and any interval
     * error; the daily-limit picker ([RuleAuthoringUiState.perAppLimits]) stays
     * as-is so an in-progress `Once` configuration survives toggling back. To
     * respect the domain invariant, an `Once` Rule must persist a null interval.
     *
     * Selecting [LimitType.Repetitive] keeps the last valid interval if one was
     * previously set (retained in [RuleAuthoringUiState.repetitiveIntervalMinutes]),
     * so switching between types does not lose a valid entry.
     */
    fun setLimitType(limitType: LimitType) {
        _uiState.update { state ->
            when (limitType) {
                LimitType.Once -> state.copy(
                    limitType = LimitType.Once,
                    repetitiveIntervalMinutes = null,
                    intervalError = false,
                )
                LimitType.Repetitive -> state.copy(
                    limitType = LimitType.Repetitive,
                )
            }
        }
    }

    /**
     * Sets the Repetitive_Interval (in whole minutes) for the Rule being authored.
     *
     * Accepts only whole minutes in 1..120 (Requirements 1.5, 1.6). A valid value
     * replaces the retained interval and clears any prior interval error. An
     * out-of-range value is REJECTED: the last valid value is retained and
     * [RuleAuthoringUiState.intervalError] is raised so the screen can show the
     * localized "1–120 whole minutes" message. Mirrors the accept/reject-and-retain
     * pattern of [setDailyLimit].
     */
    fun setRepetitiveInterval(minutes: Int) {
        _uiState.update { state ->
            if (isValidInterval(minutes)) {
                state.copy(
                    repetitiveIntervalMinutes = minutes,
                    intervalError = false,
                )
            } else {
                // Retain the last valid value; only surface the error.
                state.copy(intervalError = true)
            }
        }
    }

    /**
     * Parses a raw interval-picker string and delegates to [setRepetitiveInterval].
     * A non-integer / non-whole-minute entry is rejected the same way an
     * out-of-range value is (Requirement 1.6): the retained interval is preserved
     * and the interval error is surfaced.
     */
    fun setRepetitiveInterval(rawMinutes: String) {
        val parsed = rawMinutes.trim().toIntOrNull()
        if (parsed == null) {
            _uiState.update { it.copy(intervalError = true) }
        } else {
            setRepetitiveInterval(parsed)
        }
    }

    /**
     * Clears a surfaced interval range error once the screen has shown it.
     */
    fun clearIntervalError() {
        _uiState.update { it.copy(intervalError = false) }
    }

    /**
     * Confirms the selection and persists a Rule per selected app.
     *
     * Requires ≥1 selected app AND every selected app to have a valid daily
     * limit ([canSave]). If violated, the save is blocked, selections and limits
     * are retained, and [RuleAuthoringUiState.requiredError] is raised
     * (Requirement 2.10). On success one [InterceptionRule] is persisted per
     * selected app via [InterceptionRuleRepository] and
     * [RuleAuthoringUiState.saveSuccess] is set (Requirement 2.9). A repository
     * failure leaves the selections intact and surfaces the required/blocked
     * state without a success signal.
     *
     * There is at most one active Rule per app per user: when an active Rule
     * already exists for a selected package, its limit is UPDATED in place
     * (reusing the existing id/createdAt) rather than inserting a duplicate row.
     */
    fun save() {
        val state = _uiState.value
        if (!canSave(state.selectedPackages, state.perAppLimits, state.limitType, state.repetitiveIntervalMinutes)) {
            _uiState.update { it.copy(requiredError = true, saveSuccess = false) }
            return
        }
        // Per OQ-3 (one active Rule per app package), the selected Limit_Type +
        // interval apply to every Rule saved in this pass. `Once` persists a null
        // interval (domain invariant); `Repetitive` persists the retained valid
        // interval, which canSave has already confirmed is present.
        val savedLimitType = state.limitType
        val savedInterval = if (savedLimitType == LimitType.Repetitive) {
            state.repetitiveIntervalMinutes
        } else {
            null
        }
        viewModelScope.launch {
            val now = Instant.now()
            var allSucceeded = true
            for (pkg in state.selectedPackages) {
                val minutes = state.perAppLimits[pkg] ?: continue
                // Reuse the existing active Rule for this app if one is already
                // persisted, so re-saving the same app UPDATES its limit instead
                // of inserting a duplicate row (one Rule per app per user). A new
                // id/createdAt is minted only when no active Rule exists yet.
                val existing = interceptionRuleRepository.findActiveByPackage(userId, pkg)
                val rule = InterceptionRule(
                    id = existing?.id ?: UUID.randomUUID(),
                    userId = userId,
                    appPackageName = pkg,
                    dailyLimitMinutes = minutes,
                    limitType = savedLimitType,
                    repetitiveIntervalMinutes = savedInterval,
                    enabled = true,
                    pausedForDate = existing?.pausedForDate,
                    createdAt = existing?.createdAt ?: now,
                )
                val result = interceptionRuleRepository.save(userId, rule)
                if (result.isFailure) {
                    allSucceeded = false
                }
            }
            _uiState.update {
                if (allSucceeded) {
                    it.copy(saveSuccess = true, requiredError = false)
                } else {
                    // Preserve prior selections/limits; surface a blocked state
                    // without a success signal (Requirements 2.10, 3.9).
                    it.copy(saveSuccess = false, requiredError = true)
                }
            }
        }
    }

    /**
     * Resets the save-success flag once the screen has acknowledged it.
     */
    fun clearSaveSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    /**
     * Clears a surfaced required-app/blocked-save error once the screen has
     * shown it.
     */
    fun clearRequiredError() {
        _uiState.update { it.copy(requiredError = false) }
    }

    // --- Existing-rule management (task 18.1) -------------------------------

    /**
     * Edits the daily limit of an existing Rule (Requirements 3.3, 3.4).
     *
     * A valid whole-minute value in 1..480 is persisted via
     * [InterceptionRuleRepository.updateDailyLimit] (local-first; reflected in
     * the observed list on the next emission) and any prior edit error is
     * cleared. An invalid value is REJECTED: the repo is never called, the prior
     * value is retained, and an edit error is surfaced for [ruleId]. A repository
     * failure preserves the prior persisted state and surfaces the edit error.
     */
    fun editRuleLimit(ruleId: UUID, minutes: Int) {
        if (!isValidLimit(minutes)) {
            _uiState.update { it.copy(editLimitError = true, editErrorRuleId = ruleId) }
            return
        }
        viewModelScope.launch {
            val result = interceptionRuleRepository.updateDailyLimit(userId, ruleId, minutes)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(editLimitError = false, editErrorRuleId = null)
                } else {
                    // Prior value is preserved by the repo on failure; surface an
                    // error indication (Requirements 3.4, 3.9).
                    it.copy(editLimitError = true, editErrorRuleId = ruleId)
                }
            }
        }
    }

    /**
     * Raw-string overload of [editRuleLimit]. A non-integer / non-whole-minute
     * entry is rejected the same way an out-of-range value is (Requirement 3.4):
     * the retained limit is preserved and the edit error is surfaced.
     */
    fun editRuleLimit(ruleId: UUID, rawMinutes: String) {
        val parsed = rawMinutes.trim().toIntOrNull()
        if (parsed == null) {
            _uiState.update { it.copy(editLimitError = true, editErrorRuleId = ruleId) }
        } else {
            editRuleLimit(ruleId, parsed)
        }
    }

    /**
     * Clears a surfaced edit-limit error once the screen has shown it.
     */
    fun clearEditLimitError() {
        _uiState.update { it.copy(editLimitError = false, editErrorRuleId = null) }
    }

    /**
     * Pauses an existing Rule for the current local calendar day (Requirement
     * 3.5). Delegates to [InterceptionRuleRepository.pauseForToday] with
     * `LocalDate.now()`; the observed Flow reflects the paused state on the next
     * emission, and the Rule auto-resumes at the next day boundary.
     */
    fun pauseRuleForToday(ruleId: UUID) {
        viewModelScope.launch {
            interceptionRuleRepository.pauseForToday(userId, ruleId, LocalDate.now())
        }
    }

    /**
     * Marks a Rule as pending deletion so the screen can show its confirmation
     * dialog (Requirement 3.6). No persisted state changes until [confirmDelete].
     */
    fun requestDelete(ruleId: UUID) {
        _uiState.update { it.copy(pendingDeleteRuleId = ruleId) }
    }

    /**
     * Cancels a pending delete without removing anything.
     */
    fun cancelDelete() {
        _uiState.update { it.copy(pendingDeleteRuleId = null) }
    }

    /**
     * Confirms and performs the pending deletion (Requirement 3.7). Deletes the
     * Rule via [InterceptionRuleRepository.delete]; the observed Flow drops it
     * from [RuleAuthoringUiState.existingRules] on the next emission. Clears the
     * pending-delete marker. A no-op when no delete is pending.
     */
    fun confirmDelete() {
        val ruleId = _uiState.value.pendingDeleteRuleId ?: return
        _uiState.update { it.copy(pendingDeleteRuleId = null) }
        viewModelScope.launch {
            interceptionRuleRepository.delete(userId, ruleId)
        }
    }

    /**
     * Deletes a Rule directly by id (Requirement 3.7), bypassing the
     * confirmation marker. Callers that gate the destructive action via the
     * [requestDelete] / [confirmDelete] dialog flow should prefer those.
     */
    fun deleteRule(ruleId: UUID) {
        viewModelScope.launch {
            interceptionRuleRepository.delete(userId, ruleId)
        }
    }

    companion object {

        /**
         * Pure projection of a domain [InterceptionRule] to its screen display
         * model (Requirement 3.2), deriving the paused-for-today state against
         * [today].
         */
        fun InterceptionRule.toDisplay(today: LocalDate): ExistingRuleDisplay =
            ExistingRuleDisplay(
                id = id,
                appPackageName = appPackageName,
                dailyLimitMinutes = dailyLimitMinutes,
                enabled = enabled,
                pausedForToday = isPausedOn(today),
            )

        /**
         * Pure search filter (Requirement 2.2). Returns [apps] unchanged when
         * [query] is blank; otherwise returns the apps whose label *contains*
         * the trimmed query using case-insensitive matching. Preserves the
         * incoming (already sorted) order.
         */
        fun filterByQuery(apps: List<InstalledApp>, query: String): List<InstalledApp> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return apps
            return apps.filter { it.label.contains(trimmed, ignoreCase = true) }
        }

        /**
         * Pure daily-limit validity check (Requirements 2.6, 2.7). Because the
         * value is already an [Int] it is inherently a whole minute; validity is
         * therefore membership in the inclusive 1..480 range.
         */
        fun isValidLimit(minutes: Int): Boolean =
            minutes in RuleAuthoringUiState.MIN_LIMIT_MINUTES..RuleAuthoringUiState.MAX_LIMIT_MINUTES

        /**
         * Pure Repetitive_Interval validity check (Requirements 1.5, 1.6).
         * Because the value is already an [Int] it is inherently a whole minute;
         * validity is therefore membership in the inclusive 1..120 range. Mirrors
         * [isValidLimit] for property-test use.
         */
        fun isValidInterval(minutes: Int): Boolean =
            minutes in RuleAuthoringUiState.MIN_INTERVAL_MINUTES..RuleAuthoringUiState.MAX_INTERVAL_MINUTES

        /**
         * Pure save-eligibility check (Requirements 2.9, 2.10, 1.5). Save is
         * allowed only when at least one app is selected AND every selected app
         * has a valid daily limit recorded in [limits] (which applies to both
         * limit types, since the daily limit is always required and constrained
         * to 1..480). Additionally, when [limitType] is [LimitType.Repetitive],
         * a valid Repetitive_Interval ([interval] in 1..120) must be present.
         */
        fun canSave(
            selected: Set<String>,
            limits: Map<String, Int>,
            limitType: LimitType = LimitType.Once,
            interval: Int? = null,
        ): Boolean {
            if (selected.isEmpty()) return false
            val allLimitsValid = selected.all { pkg ->
                val minutes = limits[pkg]
                minutes != null && isValidLimit(minutes)
            }
            if (!allLimitsValid) return false
            return when (limitType) {
                LimitType.Once -> true
                LimitType.Repetitive -> interval != null && isValidInterval(interval)
            }
        }
    }
}
