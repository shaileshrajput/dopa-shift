package com.dopashift.domain.strategy

import com.dopashift.domain.entity.ChangeLogEntry
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LastWriteWinsStrategyTest {

    private val strategy = LastWriteWinsStrategy()

    private fun entry(
        timestamp: Instant,
        value: String? = "some-value",
        deviceId: String = "device-1"
    ) = ChangeLogEntry(
        id = UUID.randomUUID(),
        entityId = UUID.randomUUID(),
        entityType = "DailyTodoItem",
        field = "text",
        value = value,
        timestamp = timestamp,
        deviceId = deviceId,
        userId = UUID.randomUUID()
    )

    @Test
    fun `remote wins when remote timestamp is later`() {
        val local = entry(Instant.parse("2024-01-01T10:00:00Z"))
        val remote = entry(Instant.parse("2024-01-01T10:00:01Z"))

        val result = strategy.resolve(local, remote)

        assertIs<MergeResult.RemoteWins>(result)
        assertEquals(local, result.supersededEntry)
    }

    @Test
    fun `local wins when local timestamp is later`() {
        val local = entry(Instant.parse("2024-01-01T10:00:01Z"))
        val remote = entry(Instant.parse("2024-01-01T10:00:00Z"))

        val result = strategy.resolve(local, remote)

        assertIs<MergeResult.LocalWins>(result)
        assertEquals(remote, result.supersededEntry)
    }

    @Test
    fun `remote wins when timestamps are equal for determinism`() {
        val timestamp = Instant.parse("2024-01-01T10:00:00Z")
        val local = entry(timestamp)
        val remote = entry(timestamp)

        val result = strategy.resolve(local, remote)

        assertIs<MergeResult.RemoteWins>(result)
        assertEquals(local, result.supersededEntry)
    }
}
