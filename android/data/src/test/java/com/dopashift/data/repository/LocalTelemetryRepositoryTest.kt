package com.dopashift.data.repository

import com.dopashift.data.local.dao.TelemetryDao
import com.dopashift.data.local.entity.LocalTelemetryEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class LocalTelemetryRepositoryTest {

    private lateinit var fakeDao: FakeTelemetryDao
    private lateinit var repository: LocalTelemetryRepository

    @Before
    fun setup() {
        fakeDao = FakeTelemetryDao()
        repository = LocalTelemetryRepository(fakeDao)
    }

    @Test
    fun `recordEvent stores event in dao`() = runTest {
        val date = LocalDate.of(2024, 6, 15)
        repository.recordEvent("com.example.app", 120L, date)

        val events = fakeDao.findByDate(date.toString())
        assertEquals(1, events.size)
        assertEquals("com.example.app", events[0].appPackageName)
        assertEquals(120L, events[0].foregroundSeconds)
        assertEquals("2024-06-15", events[0].date)
    }

    @Test
    fun `getEventsForDate returns all events for that date`() = runTest {
        val date = LocalDate.of(2024, 6, 15)
        repository.recordEvent("com.app.one", 60L, date)
        repository.recordEvent("com.app.two", 90L, date)

        val events = repository.getEventsForDate(date)
        assertEquals(2, events.size)
    }

    @Test
    fun `getEventsForDate returns empty list when no events exist`() = runTest {
        val events = repository.getEventsForDate(LocalDate.of(2024, 1, 1))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `getAccumulatedSeconds returns total for app on date`() = runTest {
        val date = LocalDate.of(2024, 6, 15)
        repository.recordEvent("com.distraction.app", 30L, date)
        repository.recordEvent("com.distraction.app", 45L, date)

        val total = repository.getAccumulatedSeconds("com.distraction.app", date)
        assertEquals(75L, total)
    }

    @Test
    fun `getAccumulatedSeconds returns zero when no data exists`() = runTest {
        val total = repository.getAccumulatedSeconds("com.unknown.app", LocalDate.now())
        assertEquals(0L, total)
    }

    @Test
    fun `deleteOlderThan removes events before cutoff date`() = runTest {
        val oldDate = LocalDate.of(2024, 1, 1)
        val recentDate = LocalDate.of(2024, 6, 15)

        repository.recordEvent("com.old.app", 100L, oldDate)
        repository.recordEvent("com.recent.app", 200L, recentDate)

        repository.deleteOlderThan(LocalDate.of(2024, 3, 1))

        val oldEvents = repository.getEventsForDate(oldDate)
        val recentEvents = repository.getEventsForDate(recentDate)
        assertTrue(oldEvents.isEmpty())
        assertEquals(1, recentEvents.size)
    }

    @Test
    fun `repository has no network dependencies - compile-time verification`() {
        // This test documents the architectural constraint: LocalTelemetryRepository
        // depends ONLY on TelemetryDao (Room). It has no Retrofit, OkHttp, or API
        // dependencies. The import list of LocalTelemetryRepository.kt and this test
        // itself confirm the local-only guarantee at compile time.
        //
        // Requirements 8.1, 8.2: raw telemetry stays on device, never transmitted.
        val repo = LocalTelemetryRepository(fakeDao)
        // Class successfully instantiated with only a DAO — no network service needed
        assertTrue(repo::class.java.declaredFields.none { field ->
            field.type.name.contains("retrofit", ignoreCase = true) ||
                field.type.name.contains("okhttp", ignoreCase = true) ||
                field.type.name.contains("Api", ignoreCase = false)
        })
    }
}

/**
 * In-memory fake of TelemetryDao for unit tests.
 */
private class FakeTelemetryDao : TelemetryDao {
    private val events = mutableListOf<LocalTelemetryEvent>()

    override suspend fun upsert(event: LocalTelemetryEvent) {
        events.removeAll { it.id == event.id }
        events.add(event)
    }

    override suspend fun findByDate(date: String): List<LocalTelemetryEvent> {
        return events.filter { it.date == date }
    }

    override suspend fun getTotalForegroundSeconds(date: String, packageName: String): Long? {
        val total = events
            .filter { it.date == date && it.appPackageName == packageName }
            .sumOf { it.foregroundSeconds }
        return if (total == 0L) null else total
    }

    override suspend fun deleteOlderThan(cutoffDate: String) {
        events.removeAll { it.date < cutoffDate }
    }
}
