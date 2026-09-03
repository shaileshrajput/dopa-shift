package com.dopashift.domain.port

/**
 * Port for executing a block of work inside a single atomic transaction.
 *
 * The domain layer stays pure Kotlin: it declares the need for atomicity here,
 * while the infrastructure layer supplies a Spring-managed implementation that
 * commits on success and rolls back if the block throws.
 *
 * Used by [com.dopashift.domain.usecase.goal.CreateGoalWithHabitUseCase] to persist
 * a goal and its dependent habit track (plus checkpoints) as one unit, so no
 * partially created state (an orphaned habit or a goal without its habit) is ever
 * committed.
 */
interface TransactionRunner {
    /**
     * Executes [block] within a single transaction.
     *
     * If [block] completes normally its result is returned and the transaction commits.
     * If [block] throws, the transaction rolls back and the exception propagates.
     */
    suspend fun <T> execute(block: suspend () -> T): T
}
