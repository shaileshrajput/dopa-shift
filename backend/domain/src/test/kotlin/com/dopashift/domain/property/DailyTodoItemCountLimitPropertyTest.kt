package com.dopashift.domain.property

import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.exception.LimitExceededException
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.usecase.todo.CreateTodoUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.localDate
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property 17: Daily Todo Item Count Limit
 *
 * For any user on any given day, attempting to create a DailyTodoItem when 100 already exist
 * for that day SHALL be rejected — the count SHALL never exceed 100 per user per day.
 *
 * **Validates: Requirements 4.2**
 *
 * Tag: Feature: dopa-shift, Property 17: Daily Todo Item Count Limit
 */
class DailyTodoItemCountLimitPropertyTest : FunSpec({

    tags(
        io.kotest.core.Tag("Feature: dopa-shift"),
        io.kotest.core.Tag("Property 17: Daily Todo Item Count Limit")
    )

    test("Property 17: Creating the 101st todo item when 100 already exist SHALL be rejected with LimitExceededException") {
        checkAll(100, Arb.uuid(), Arb.localDate()) { userId, dayDate ->
            val repository = InMemoryDailyTodoRepository()
            val useCase = CreateTodoUseCase(repository)

            // Pre-fill the repository with exactly 100 items for this user/day
            repeat(100) { i ->
                val item = DailyTodoItem(
                    id = UUID.randomUUID(),
                    userId = userId,
                    text = "Task ${i + 1}",
                    dueDateTime = null,
                    isCompleted = false,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    dayDate = dayDate
                )
                repository.save(item)
            }

            // Verify 100 items exist
            repository.countByUserIdAndDate(userId, dayDate) shouldBe 100

            // The 101st creation attempt should throw LimitExceededException
            val exception = runCatching {
                useCase.execute(userId = userId, text = "Item 101", dayDate = dayDate)
            }.exceptionOrNull()

            exception.shouldBeInstanceOf<LimitExceededException>()
        }
    }

    test("Property 17: Creating a todo item when count is below 100 SHALL succeed") {
        checkAll(100, Arb.uuid(), Arb.localDate(), Arb.int(0..99)) { userId, dayDate, existingCount ->
            val repository = InMemoryDailyTodoRepository()
            val useCase = CreateTodoUseCase(repository)

            // Pre-fill the repository with existingCount items (0 to 99)
            repeat(existingCount) { i ->
                val item = DailyTodoItem(
                    id = UUID.randomUUID(),
                    userId = userId,
                    text = "Task ${i + 1}",
                    dueDateTime = null,
                    isCompleted = false,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                    dayDate = dayDate
                )
                repository.save(item)
            }

            // Verify existing count
            repository.countByUserIdAndDate(userId, dayDate) shouldBe existingCount

            // Creation should succeed
            val result = useCase.execute(userId = userId, text = "New item", dayDate = dayDate)
            result.userId shouldBe userId
            result.dayDate shouldBe dayDate
            result.text shouldBe "New item"

            // The count should now be existingCount + 1
            repository.countByUserIdAndDate(userId, dayDate) shouldBe existingCount + 1
        }
    }
})

/**
 * In-memory implementation of DailyTodoRepository for property testing.
 * Stores items in a mutable list, scoped by userId and dayDate.
 */
private class InMemoryDailyTodoRepository : DailyTodoRepository {
    private val store = mutableListOf<DailyTodoItem>()

    override suspend fun findById(id: UUID): DailyTodoItem? =
        store.find { it.id == id }

    override suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> =
        store.filter { it.userId == userId && it.dayDate == date }

    override suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int =
        store.count { it.userId == userId && it.dayDate == date }

    override suspend fun save(item: DailyTodoItem): DailyTodoItem {
        store.removeAll { it.id == item.id }
        store.add(item)
        return item
    }

    override suspend fun delete(id: UUID) {
        store.removeAll { it.id == id }
    }
}
