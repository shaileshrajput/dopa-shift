package com.dopashift.domain.strategy

import com.dopashift.domain.entity.ChangeLogEntry
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeleteWinsStrategyTest {

    private val strategy = DeleteWinsStrategy()
    private val baseEntityId = UUID.randomUUID()
    private val baseUserId = UUID.randomUUID()

    private fun entry(
        timestamp: Instant,
        value: String? = "some-value",
        deviceId: String = "device-1"
    ) = ChangeLogEntry(
        id = UUID.randomUUID(),
        entityId = baseEntityId,
        entityType = "DailyTodoItem",
        field = "text",
        value = value,
        timestamp = timestamp,
        deviceId = deviceId,
        userId = baseUserId
    )

    @Test
    fun `remote delete wins over local edit`() {
        val local = entry(Instant.parse("2024-01-01T10:00:01Z"), value = "edited text")
        val remote = entry(Instant.parse("2024-01-01T10:00:00Z"), value = null)

        val result = strategy.resolve(local, remote)

        assertIs<MergeResult.RemoteWins>(result)
        assertEquals(local, result.supersededEntry)
    }

    @Test
    fun `local delete wins over remote edit`() {
        val local = entry(Instant.parse("2024-01-01T10:00:00Z"), value = null)
        val remote = entry(Instant.parse("2024-01-01T10:00:01Z"), value = "edited text")

        val result = strategy.resolve(local, remote)

        assertIs<MergeResult.LocalWins>(result)
        assertEquals(remote, result.supersededEntry)
    }

    @Test
    fun `remote delete wins when both are deletes`() {
        val local = entry(Instant.parse("2024-01-01T10:00:01Z"), value = null)
        val remote = entry(Instant.parse("2024-01-01T10:00:00Z"), value = null)

        val result = strategy.resolve(local, remote)

        // When both are deletes, remote.value == null is checked first, so remote wins
        assertIs<MergeResult.RemoteWins>(result)
        assertEquals(local, result.supersededEntry)
    }

    @Test
    fun `falls back to LastWriteWins when neither is a delete`() {
        val local = entry(Instant.parse("2024-01-01T10:00:00Z"), value = "old text")
        val remote = entry(Instant.parse("2024-01-01T10:00:01Z"), value = "new text")

        val result = strategy.resolve(local, remote)

        // remote timestamp is later, so remote wins via LWW fallback
        assertIs<MergeResult.RemoteWins>(result)
        assertEquals(local, result.supersededEntry)
    }

    @Test
    fun `falls back to LastWriteWins local wins when local timestamp is later`() {
        val local = entry(Instant.parse("2024-01-01T10:00:01Z"), value = "newer text")
        val remote = entry(Instant.parse("2024-01-01T10:00:00Z"), value = "older text")

        val result = strategy.resolve(local, remote)

        assertIs<MergeResult.LocalWins>(result)
        assertEquals(remote, result.supersededEntry)
    }

    @Test
    fun `delete wins regardless of timestamp ordering`() {
        // Remote delete with earlier timestamp still wins over local edit with later timestamp
        val local = entry(Instant.parse("2024-01-01T10:00:05Z"), value = "very late edit")
        val remote = entry(Instant.parse("2024-01-01T10:00:00Z"), value = null)

        val result = strategy.resolve(local, remote)

        assertIs<MergeResult.RemoteWins>(result)
        assertEquals(local, result.supersededEntry)
    }
}
