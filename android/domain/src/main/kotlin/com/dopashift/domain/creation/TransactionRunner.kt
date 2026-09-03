package com.dopashift.domain.creation

/**
 * Runs a block of work as one atomic local (Room) transaction (DQC-3.3).
 *
 * Used by Inline_Goal_Capture so a goal and its dependent habit are persisted as a
 * single unit: if the block throws at any point, the whole transaction rolls back and
 * no partial state (and no committed Change_Log events for the aborted unit) remains.
 *
 * Declared in `domain` as a pure-Kotlin port; the Room-backed implementation lives in
 * the `data` module (`RoomTransactionRunner`).
 */
interface TransactionRunner {

    /**
     * Executes [block] inside a single atomic transaction and returns its result.
     * If [block] throws, the transaction is rolled back and the exception propagates.
     *
     * @param block the suspending unit of work to run atomically.
     * @return the value produced by [block] on successful commit.
     */
    suspend fun <T> inTransaction(block: suspend () -> T): T
}
