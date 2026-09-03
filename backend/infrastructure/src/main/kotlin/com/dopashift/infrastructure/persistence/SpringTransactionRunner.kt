package com.dopashift.infrastructure.persistence

import com.dopashift.domain.port.TransactionRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Spring-managed [TransactionRunner]: runs the supplied block inside a single
 * database transaction that commits on success and rolls back if the block throws.
 *
 * The block is executed on a single blocking thread bound to the transaction so that
 * all JDBC writes participate in the same transaction. This gives Inline_Goal_Capture
 * its server-side atomicity (DQC-3.3): a goal and its dependent habit track are
 * committed together or not at all.
 */
@Component
class SpringTransactionRunner(
    transactionManager: PlatformTransactionManager
) : TransactionRunner {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    override suspend fun <T> execute(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            transactionTemplate.execute {
                // Bind the transaction to this thread for the whole block. Any
                // exception propagates out and marks the transaction for rollback.
                runBlocking { block() }
            } as T
        }
}
