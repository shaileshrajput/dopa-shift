package com.dopashift.data.repository

import androidx.room.withTransaction
import com.dopashift.data.local.DopaShiftDatabase
import com.dopashift.domain.creation.TransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [TransactionRunner].
 *
 * Runs the given block inside a single Room transaction via [withTransaction] (the
 * room-ktx coroutine-aware extension). The block executes atomically: if it throws at
 * any point, Room rolls the whole transaction back so no partial state — and no
 * committed Change_Log events for the aborted unit — remains (DQC-3.3). On success the
 * transaction commits and the block's result is returned.
 *
 * Used by Inline_Goal_Capture so a goal and its dependent habit are persisted as one unit.
 */
@Singleton
class RoomTransactionRunner @Inject constructor(
    private val database: DopaShiftDatabase
) : TransactionRunner {

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        return database.withTransaction {
            block()
        }
    }
}
