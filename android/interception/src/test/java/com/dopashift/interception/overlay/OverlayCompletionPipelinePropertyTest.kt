package com.dopashift.interception.overlay

// Feature: screen-time-interception-engine, Property 14

import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

/**
 * Property 14: Overlay action uses the same pipeline as normal completion.
 *
 * Validates: Requirements 4.8 — WHEN the user records an action on the Intercept_Screen,
 * THE Android_App SHALL write that action to the same local data pipelines used elsewhere.
 *
 * For any randomized *pending* [DailyTodoItem], completing it through
 * [OverlayTodoCompletionHandler.completeTodo] must:
 *  (a) persist the item as completed via [DailyTodoRepository.save] (the store reflects
 *      `isCompleted = true`), and
 *  (b) append exactly one [ChangeLogEntry] via [ChangeLogRepository.append] whose fields
 *      match the normal-completion change-log contract: `entityType = "DailyTodoItem"`,
 *      `field = "isCompleted"`, `value = "true"`, `entityId = todoId`, and
 *      `userId = the todo's userId`.
 *
 * It must also be idempotent: completing an already-completed item does not double-append
 * (per the handler's early return), so the same local pipeline is never invoked twice for
 * a no-op.
 *
 * This is a randomized-input property test (JUnit4 + `kotlin.random.Random` +
 * `repeat(N>=100)` + `kotlinx.coroutines.test.runTest`), matching the established
 * interception-module style; Kotest is not on this module's test classpath.
 */
class OverlayCompletionPipelinePropertyTest {

    /**
     * In-memory [DailyTodoRepository] fake. Backs [findById]/[save] with a mutable map so
     * assertions can read the persisted state exactly as the real store would expose it.
     */
    private class FakeDailyTodoRepository : DailyTodoRepository {
        val store = mutableMapOf<UUID, DailyTodoItem>()
        var saveCount = 0

        override suspend fun findById(id: UUID): DailyTodoItem? = store[id]

        override suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> =
            filterByUserIdAndDate(userId, date)

        override suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int =
            filterByUserIdAndDate(userId, date).size

        override suspend fun save(item: DailyTodoItem): DailyTodoItem {
            saveCount++
            store[item.id] = item
            return item
        }

        override suspend fun delete(id: UUID) {
            store.remove(id)
        }

        override fun observeByUserIdAndDate(userId: UUID, date: LocalDate): Flow<List<DailyTodoItem>> =
            flowOf(filterByUserIdAndDate(userId, date))

        private fun filterByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> =
            store.values.filter { it.userId == userId && it.dayDate == date }
    }

    /**
     * In-memory [ChangeLogRepository] fake capturing every appended [ChangeLogEntry] so the
     * test can assert the exact change-log contract and count.
     */
    private class FakeChangeLogRepository : ChangeLogRepository {
        val appended = mutableListOf<ChangeLogEntry>()

        override suspend fun append(entry: ChangeLogEntry) {
            appended.add(entry)
        }

        override suspend fun findSince(userId: UUID, since: Instant): List<ChangeLogEntry> =
            appended.filter { it.userId == userId && !it.timestamp.isBefore(since) }

        override suspend fun countByUserId(userId: UUID): Long =
            appended.count { it.userId == userId }.toLong()

        override suspend fun archiveOlderThan(cutoff: Instant) {
            appended.removeAll { it.timestamp.isBefore(cutoff) }
        }

        override fun observeRecent(userId: UUID, limit: Int, offset: Int): Flow<List<ChangeLogEntry>> =
            flowOf(appended.filter { it.userId == userId }.drop(offset).take(limit))
    }

    private fun randomTodoText(random: Random): String {
        val len = random.nextInt(1, 40)
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9') + ' '
        // Guarantee at least one non-space so the 1..500 invariant holds.
        return "t" + (1 until len).joinToString("") { chars[random.nextInt(chars.size)].toString() }
    }

    @Test
    fun `property - overlay completion uses the same store and change-log pipeline`() = runTest {
        val random = Random(seed = 41414)

        repeat(200) {
            val todoRepo = FakeDailyTodoRepository()
            val changeLogRepo = FakeChangeLogRepository()
            val handler = OverlayTodoCompletionHandler(todoRepo, changeLogRepo)

            val todoId = UUID(random.nextLong(), random.nextLong())
            val userId = UUID(random.nextLong(), random.nextLong())
            val createdAt = Instant.ofEpochSecond(random.nextLong(1_000_000L, 2_000_000_000L))
            val pending = DailyTodoItem(
                id = todoId,
                userId = userId,
                text = randomTodoText(random),
                dueDateTime = if (random.nextBoolean()) createdAt.plusSeconds(random.nextLong(1, 86_400)) else null,
                isCompleted = false,
                createdAt = createdAt,
                updatedAt = createdAt,
                dayDate = LocalDate.ofEpochDay(random.nextLong(0L, 30_000L))
            )
            todoRepo.store[todoId] = pending

            // Act: complete via the overlay handler.
            val result = handler.completeTodo(todoId)

            // (a) The store reflects the completed state.
            assertNotNull("completeTodo should return the updated item", result)
            assertTrue("returned item should be completed", result!!.isCompleted)
            val persisted = todoRepo.store[todoId]
            assertNotNull(persisted)
            assertTrue("persisted item should be completed", persisted!!.isCompleted)
            assertEquals("save should be invoked exactly once", 1, todoRepo.saveCount)

            // (b) Exactly one change-log entry matching the normal-completion contract.
            assertEquals("exactly one Change_Log entry should be appended", 1, changeLogRepo.appended.size)
            val entry = changeLogRepo.appended.single()
            assertEquals("DailyTodoItem", entry.entityType)
            assertEquals("isCompleted", entry.field)
            assertEquals("true", entry.value)
            assertEquals(todoId, entry.entityId)
            assertEquals(userId, entry.userId)

            // Idempotency: completing an already-completed item is a no-op for both pipelines.
            val secondResult = handler.completeTodo(todoId)
            assertNotNull(secondResult)
            assertTrue(secondResult!!.isCompleted)
            assertEquals("save should not be invoked again on a completed item", 1, todoRepo.saveCount)
            assertEquals(
                "no additional Change_Log entry should be appended for an already-completed item",
                1,
                changeLogRepo.appended.size
            )
        }
    }
}
