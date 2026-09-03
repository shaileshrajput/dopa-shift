package com.dopashift.interception.overlay

import com.dopashift.domain.creation.CorrelationId
import com.dopashift.domain.creation.CreateGoalCommand
import com.dopashift.domain.creation.CreationOrigin
import com.dopashift.domain.creation.CreationResult
import com.dopashift.domain.creation.GoalWithHabit
import com.dopashift.domain.creation.HabitAuthoring
import com.dopashift.domain.creation.InlineGoalWithHabitCommand
import com.dopashift.domain.creation.usecase.CreateGoalWithHabitUseCase
import com.dopashift.ui.quickcreate.InlineGoalFields
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reaches Inline_Goal_Capture from within the Intercept_Overlay (DQC-5.10).
 *
 * When a user with zero [com.dopashift.domain.entity.GoalProfile]s is intercepted, the overlay
 * must let them create a goal (with its dependent habit) on the spot rather than dead-ending. This
 * handler is the interception module's entry point to that flow. It does **not** implement any of
 * the creation logic itself: it drives the *same* [CreateGoalWithHabitUseCase] the Dashboard habit
 * sheet drives (task 10.1), with **no parallel data path** (design DQC-5.10). The only difference
 * is [CreationOrigin.INTERCEPT_OVERLAY], carried on the goal command so diagnostics can attribute
 * the action to the overlay.
 *
 * The use case runs the goal + habit persistence as one atomic local transaction, emits the goal's
 * Change_Log events before the habit's, and returns an Undo token spanning both entities — exactly
 * as it does from the Dashboard, because it is the same use case instance (Hilt provides a single
 * binding via `QuickCreateUseCaseModule`).
 *
 * Requirements: 5.10
 */
@Singleton
class OverlayInlineGoalCaptureHandler @Inject constructor(
    private val createGoalWithHabit: CreateGoalWithHabitUseCase
) {

    /**
     * Creates a goal and its dependent habit atomically from inside the overlay.
     *
     * A single [CorrelationId] spans the whole transaction (goal + habit), matching the
     * Dashboard's Inline_Goal_Capture behaviour (DQC-6.3). The goal command is tagged with
     * [CreationOrigin.INTERCEPT_OVERLAY] so the action is attributed to the overlay without
     * introducing a separate code path.
     *
     * @param userId the authenticated owner; every read/write is scoped by it.
     * @param inlineGoal the minimum valid goal fields collected inline (name, category, >=1 keyword).
     * @param authoring how the habit's 30 checkpoints are sourced (Template | Llm | Manual).
     * @param withReminder whether a daily checkpoint Reminder was requested (offered, not required).
     * @return the [CreationResult] from the shared use case: [CreationResult.Success] carrying the
     *   created [GoalWithHabit] and a whole-unit Undo token, or a validation/rejection/failure
     *   outcome with nothing persisted.
     */
    suspend fun captureGoalWithHabit(
        userId: UUID,
        inlineGoal: InlineGoalFields,
        authoring: HabitAuthoring,
        withReminder: Boolean = false
    ): CreationResult<GoalWithHabit> {
        val correlationId = CorrelationId.random()
        val command = InlineGoalWithHabitCommand(
            goal = CreateGoalCommand(
                userId = userId,
                name = inlineGoal.name,
                category = inlineGoal.category,
                keywords = inlineGoal.keywords,
                description = inlineGoal.description,
                correlationId = correlationId,
                origin = CreationOrigin.INTERCEPT_OVERLAY
            ),
            authoring = authoring,
            withReminder = withReminder,
            correlationId = correlationId
        )

        val result = createGoalWithHabit(command)
        when (result) {
            is CreationResult.Success ->
                Timber.i(
                    "OverlayInlineGoalCaptureHandler: Inline_Goal_Capture succeeded from overlay " +
                        "(origin=INTERCEPT_OVERLAY, correlationId=%s)",
                    correlationId.value
                )
            is CreationResult.ValidationError ->
                Timber.w("OverlayInlineGoalCaptureHandler: inline goal validation failed")
            is CreationResult.Rejected ->
                Timber.w("OverlayInlineGoalCaptureHandler: inline goal rejected: %s", result.reason)
            is CreationResult.Failure ->
                Timber.w(result.cause, "OverlayInlineGoalCaptureHandler: Inline_Goal_Capture failed")
        }
        return result
    }
}
