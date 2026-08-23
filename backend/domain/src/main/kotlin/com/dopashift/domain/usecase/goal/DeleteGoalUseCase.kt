package com.dopashift.domain.usecase.goal

import com.dopashift.domain.exception.GoalNotFoundException
import com.dopashift.domain.exception.InvalidReassignmentTargetException
import com.dopashift.domain.repository.GoalChecklistItemRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.InterceptionRuleRepository
import java.util.UUID

/**
 * Deletes a goal after handling all dependent items according to the chosen action.
 *
 * Dependent items:
 * - GoalChecklistItems
 * - HabitTracks
 * - InterceptionRules
 *
 * The user must choose one of:
 * - DELETE_ALL: delete all dependent items, then delete the goal
 * - REASSIGN(targetGoalId): move all dependents to the target goal, then delete the goal
 *
 * Requirements: 1.4, 1.5, 1.6
 */
class DeleteGoalUseCase(
    private val goalRepository: GoalRepository,
    private val goalChecklistItemRepository: GoalChecklistItemRepository,
    private val habitTrackRepository: HabitTrackRepository,
    private val interceptionRuleRepository: InterceptionRuleRepository
) {

    suspend fun execute(command: DeleteGoalCommand) {
        val goal = goalRepository.findById(command.goalId)
            ?: throw GoalNotFoundException(command.goalId.toString())

        when (command.action) {
            is DeleteGoalAction.DeleteAll -> {
                goalChecklistItemRepository.deleteByGoalId(goal.id)
                habitTrackRepository.deleteByGoalId(goal.id)
                interceptionRuleRepository.deleteByGoalId(goal.id)
            }

            is DeleteGoalAction.Reassign -> {
                val targetId = command.action.targetGoalId
                validateReassignmentTarget(targetId, command.goalId)

                goalChecklistItemRepository.reassignToGoal(goal.id, targetId)
                habitTrackRepository.reassignToGoal(goal.id, targetId)
                interceptionRuleRepository.reassignToGoal(goal.id, targetId)
            }
        }

        goalRepository.delete(goal.id)
    }

    private suspend fun validateReassignmentTarget(targetGoalId: UUID, sourceGoalId: UUID) {
        if (targetGoalId == sourceGoalId) {
            throw InvalidReassignmentTargetException(
                "Cannot reassign dependents to the same goal being deleted"
            )
        }
        goalRepository.findById(targetGoalId)
            ?: throw InvalidReassignmentTargetException(
                "Target goal for reassignment not found: $targetGoalId"
            )
    }
}

data class DeleteGoalCommand(
    val goalId: UUID,
    val action: DeleteGoalAction
)
