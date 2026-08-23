package com.dopashift.api.config

import com.dopashift.application.sync.SyncProcessor
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.ConflictHistoryRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring configuration for sync engine beans.
 * Wires the [SyncProcessor] with its repository dependencies.
 */
@Configuration
class SyncConfig {

    @Bean
    fun syncProcessor(
        changeLogRepository: ChangeLogRepository,
        conflictHistoryRepository: ConflictHistoryRepository
    ): SyncProcessor {
        return SyncProcessor(
            changeLogRepository = changeLogRepository,
            conflictHistoryRepository = conflictHistoryRepository
        )
    }
}
