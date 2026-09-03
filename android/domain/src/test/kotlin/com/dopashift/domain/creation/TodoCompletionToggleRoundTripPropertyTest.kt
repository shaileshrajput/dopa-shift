package com.dopashift.domain.creation

// Feature: dashboard-quick-create, Property 20: To-Do Completion Toggle Round-Trip

import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.repository.DailyTodoRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property-based test for the to-do completion toggle round-trip.
 *
 * Feature: dashboard-quick-create, Property 20: To-Do Completion Toggle Round-Trip
 * Validates: Requirements 4.8
 *
 * Requirement 4.8 completes a to-do by applying strikethrough + 50% opacity, retains that
 * state, and allows un-checking. The domain-level invariant under test is that toggling a
 * [DailyTodoItem]'s `isCompleted` flag round-trips: completing then un-checking returns the
 * item to its original completion state, and the item is *retained* (never deleted) across
 * toggles while its identity fields (`id`, `text`, `dayDate`, plus `userId`/`createdAt`/
 * `dueDateTime`) are preserved.
 *
 * The toggle is modeled as `item.copy(isCompleted = X, updatedAt = ...)`, matching how the
 * UI/data layers persist a completion change. The round-trip is exercised both at the pure
 * entity level and through an in-memory [DailyTodoRepository] fake (save then find returns
 * the toggled item; toggling back restores state).
 *
 * Kotest property testing, >= 100 iterations, matching the sibling `domain` property tests.
 */
class TodoCompletionToggleRoundTripPropertyTest : StringSpec({

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    val config = PropTestConfig(iterations = 200)

    // Instants that stay within a sane range and preserve strict ordering for updatedAt bumps.
    val instants: Arb<Instant> =
        Arb.long(min = 0L, max = 4_000_000_000L).map { Instant.ofEpochSecond(it) }

    val dayDates: Arb<LocalDate> = Arb.of(
        LocalDate.of(2024, 1, 1),
        LocalDate.of(2024, 6, 15),
        LocalDate.of(2025, 2, 28),
        LocalDate.of(2030, 12, 31)
    )

    // Non-blank text within the entity's 1..500 bound (the entity's init would reject others).
    val validText: Arb<String> = Arb.string(minSize = 1, maxSize = 500).filter { it.isNotBlank() }

    // Arbitrary well-formed DailyTodoItems.
    val todos: Arb<DailyTodoItem> = Arb.bind(
        validText,
        Arb.boolean(),
        instants,
        dayDates,
        instants.orNull(0.3)
    ) { text, completed, ts, dayDate, due ->
        DailyTodoItem(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            text = text,
            dueDateTime = due,
            isCompleted = completed,
            createdAt = ts,
            updatedAt = ts,
            dayDate = dayDate
        )
    }

    // A completion toggle: flip isCompleted and bump updatedAt (never touching identity fields).
    fun DailyTodoItem.toggle(at: Instant): DailyTodoItem =
        copy(isCompleted = !isCompleted, updatedAt = at)

    // ---- Entity-level round-trip (DQC-4.8) ----

    "Property 20 (DQC-4.8): single toggle flips isCompleted and preserves all other fields" {
        checkAll(config, todos, instants) { original, at ->
            val toggled = original.toggle(at)

            // The only value that changes is isCompleted (and the audit timestamp).
            toggled.isCompleted shouldBe !original.isCompleted
            toggled.id shouldBe original.id
            toggled.userId shouldBe original.userId
            toggled.text shouldBe original.text
            toggled.dayDate shouldBe original.dayDate
            toggled.dueDateTime shouldBe original.dueDateTime
            toggled.createdAt shouldBe original.createdAt
        }
    }

    "Property 20 (DQC-4.8): complete then un-check returns to the original completion state" {
        checkAll(config, todos, instants, instants) { original, t1, t2 ->
            val completed = original.toggle(t1)
            val unchecked = completed.toggle(t2)

            // Round-trip restores the original completion flag and every identity field.
            unchecked.isCompleted shouldBe original.isCompleted
            unchecked.id shouldBe original.id
            unchecked.userId shouldBe original.userId
            unchecked.text shouldBe original.text
            unchecked.dayDate shouldBe original.dayDate
            unchecked.dueDateTime shouldBe original.dueDateTime
            unchecked.createdAt shouldBe original.createdAt
        }
    }

    "Property 20 (DQC-4.8): a double round-trip is the identity on isCompleted" {
        checkAll(config, todos, instants, instants, instants, instants) { original, t1, t2, t3, t4 ->
            val doubleRoundTrip = original
                .toggle(t1)
                .toggle(t2)
                .toggle(t3)
                .toggle(t4)

            doubleRoundTrip.isCompleted shouldBe original.isCompleted
        }
    }

    // ---- Repository round-trip: item is retained (not deleted) across toggles (DQC-4.8) ----

    "Property 20 (DQC-4.8): save then find returns the toggled item; toggling back restores it" {
        checkAll(config, todos, instants, instants) { original, t1, t2 ->
            runTest {
                val repo = InMemoryDailyTodoRepository()
                repo.save(original)

                // Toggle: persist the toggled item under the same id (flips completion).
                val completed = original.toggle(t1)
                repo.save(completed)

                val afterComplete = repo.findById(original.id)
                afterComplete shouldNotBe null
                afterComplete!!.isCompleted shouldBe !original.isCompleted
                afterComplete.text shouldBe original.text
                afterComplete.dayDate shouldBe original.dayDate

                // The item is retained (still exactly one row for this user/day), not deleted.
                val listAfterComplete = repo.findByUserIdAndDate(original.userId, original.dayDate)
                listAfterComplete.map { it.id } shouldContain original.id
                listAfterComplete.count { it.id == original.id } shouldBe 1

                // Un-check: persist the toggled-back item.
                val unchecked = completed.toggle(t2)
                repo.save(unchecked)

                val afterUncheck = repo.findById(original.id)
                afterUncheck shouldNotBe null
                afterUncheck!!.isCompleted shouldBe original.isCompleted
                afterUncheck.text shouldBe original.text
                afterUncheck.dayDate shouldBe original.dayDate
                afterUncheck.dueDateTime shouldBe original.dueDateTime
                afterUncheck.createdAt shouldBe original.createdAt

                // Still retained after the full round-trip.
                repo.findByUserIdAndDate(original.userId, original.dayDate)
                    .count { it.id == original.id } shouldBe 1
            }
        }
    }
})

/**
 * Minimal in-memory [DailyTodoRepository] fake: `save` upserts by id, so toggling a to-do's
 * completion updates the retained row rather than duplicating or deleting it.
 */
private class InMemoryDailyTodoRepository : DailyTodoRepository {
    private val store = LinkedHashMap<UUID, DailyTodoItem>()

    override suspend fun findById(id: UUID): DailyTodoItem? = store[id]

    override suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> =
        store.values.filter { it.userId == userId && it.dayDate == date }

    override suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int =
        findByUserIdAndDate(userId, date).size

    override suspend fun save(item: DailyTodoItem): DailyTodoItem {
        store[item.id] = item
        return item
    }

    override suspend fun delete(id: UUID) {
        store.remove(id)
    }

    override fun observeByUserIdAndDate(userId: UUID, date: LocalDate): Flow<List<DailyTodoItem>> =
        flowOf(store.values.filter { it.userId == userId && it.dayDate == date })
}
