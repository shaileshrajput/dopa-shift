package com.dopashift.domain.creation

/**
 * A single, self-contained snapshot of the counts that distinguish Zero_State from
 * Partial_State from a fully populated Dashboard (DQC-5.3).
 *
 * The Dashboard render path derives all of its state from one [DashboardSummary] so
 * it never issues more than one network call for state (Backend Delta 2). Zero_State
 * versus Partial_State is a pure function of the three counts; onboarding status is
 * carried alongside so the UI can suppress Zero_State prompts for entity types created
 * during onboarding (DQC-5.7) without a second query.
 *
 * @property userId the authenticated user this summary is scoped to; every count is
 *   derived from data owned by this user only (never a client-supplied id alone).
 * @property activeGoalCount number of the user's active Goal_Profiles.
 * @property todayTodoCount number of the user's DailyTodoItems for today (user time zone).
 * @property activeHabitCount number of the user's active Habit_Tracks.
 * @property onboardingStatus the persisted onboarding outcome (DQC-5.7, DQC-5.8).
 */
data class DashboardSummary(
    val userId: UserId,
    val activeGoalCount: Int,
    val todayTodoCount: Int,
    val activeHabitCount: Int,
    val onboardingStatus: OnboardingStatus
) {
    init {
        require(activeGoalCount >= 0) { "activeGoalCount must not be negative" }
        require(todayTodoCount >= 0) { "todayTodoCount must not be negative" }
        require(activeHabitCount >= 0) { "activeHabitCount must not be negative" }
    }

    /**
     * True only when the user has zero goals, zero to-dos, and zero habits — the sole
     * condition under which the Dashboard renders a whole-screen empty state (DQC-5.3).
     */
    val isZeroState: Boolean
        get() = activeGoalCount == 0 && todayTodoCount == 0 && activeHabitCount == 0

    /**
     * Resolves how a single Dashboard [section] should render: [SectionRenderState.POPULATED]
     * when that section's entity type has data, otherwise [SectionRenderState.INLINE_PROMPT]
     * so the capability stays discoverable rather than hidden (DQC-5.3, DQC-5.6).
     *
     * @param section the Dashboard section to resolve.
     * @return the render state for that section given the current counts.
     */
    fun sectionState(section: DashboardSection): SectionRenderState = when (section) {
        DashboardSection.GOALS -> if (activeGoalCount > 0) SectionRenderState.POPULATED else SectionRenderState.INLINE_PROMPT
        DashboardSection.TODOS -> if (todayTodoCount > 0) SectionRenderState.POPULATED else SectionRenderState.INLINE_PROMPT
        DashboardSection.HABITS -> if (activeHabitCount > 0) SectionRenderState.POPULATED else SectionRenderState.INLINE_PROMPT
    }

    /**
     * Whether the Zero_State creation prompt for [section] should be suppressed because that
     * entity type was already created during onboarding (DQC-5.7).
     *
     * When the user completes onboarding normally, onboarding produces a Goal_Profile (parent
     * Req 17 AC4), so the Dashboard SHALL NOT re-prompt the user to create a goal even in the
     * transient moment before the summary reflects that goal. A section that already has data is
     * never a prompt regardless of onboarding, so suppression only ever matters while a section's
     * count is zero.
     *
     * Suppression applies only when onboarding was [OnboardingStatus.COMPLETED]; a user who
     * skipped ([OnboardingStatus.SKIPPED]) or never started ([OnboardingStatus.NOT_STARTED])
     * onboarding created nothing during it and therefore keeps every empty-section prompt so the
     * capability stays discoverable (DQC-5.2, DQC-5.6).
     *
     * @param section the Dashboard section whose Zero_State prompt is being resolved.
     * @return true when the prompt for [section] should be hidden; false when it should show.
     */
    fun suppressesZeroStatePrompt(section: DashboardSection): Boolean =
        onboardingStatus == OnboardingStatus.COMPLETED && section in ONBOARDING_CREATED_SECTIONS

    private companion object {
        /**
         * The entity-type sections that a normally completed onboarding produces (parent Req 17
         * AC4 always yields a Goal_Profile), whose Zero_State prompts are therefore suppressed
         * for a user who completed onboarding (DQC-5.7).
         */
        val ONBOARDING_CREATED_SECTIONS = setOf(DashboardSection.GOALS)
    }
}

/**
 * The distinct, independently renderable sections of the Dashboard whose state is
 * resolved from a [DashboardSummary] (DQC-5.3, DQC-5.6).
 */
enum class DashboardSection {
    /** The Active Goals section. */
    GOALS,

    /** The Today's Focus (DailyTodoItem) section. */
    TODOS,

    /** The Habit_Track checkpoint section. */
    HABITS
}

/**
 * How a Dashboard section renders in Partial_State: with its data, or as an inline
 * creation prompt when empty (never a whole-screen empty state) (DQC-5.3, DQC-5.6).
 */
enum class SectionRenderState {
    /** The section has data and renders its content normally. */
    POPULATED,

    /** The section is empty and renders as an inline creation prompt. */
    INLINE_PROMPT
}
