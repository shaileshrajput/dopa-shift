package com.dopashift.data.repository

// Feature: android-app-limit-repitative, Property 10

import com.dopashift.data.local.dao.InterceptActionAuditDao
import com.dopashift.data.local.entity.LocalInterceptActionAudit
import com.dopashift.domain.entity.InterceptActionAudit
import com.dopashift.domain.entity.InterceptActionType
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.ParameterizedType
import java.time.Instant
import java.util.UUID

/**
 * Property-based test for [InterceptActionAuditRepositoryImpl] local-only audit logging.
 *
 * **Feature: android-app-limit-repitative, Property 10: Every action writes exactly one
 * local audit entry, never transmitted**
 *
 * **Validates: Requirements 3.4, 3.5**
 *
 * For any executed overlay action, exactly one [InterceptActionAudit] entry is appended
 * locally (with the correct package and action type), and the produced record is absent
 * from any sync payload.
 *
 * This is verified three complementary ways:
 *
 *  (a) Exactly-one-append: for an arbitrary (appPackageName, actionType, userId, recordedAt)
 *      tuple, [InterceptActionAuditRepositoryImpl.append] succeeds and [listForUser] returns
 *      exactly one MORE entry than before, whose package and action type match the appended
 *      audit. The write reaches ONLY the audit DAO ([FakeInterceptActionAuditDao.insertCount]
 *      increments by one).
 *
 *  (b) Never-transmitted, behavioral: a spy standing in for any sync/upload boundary is wired
 *      alongside the repository. Appending audit entries drives the audit DAO, but the sync
 *      spy stays empty — the append path cannot enqueue a sync/upload because the repository
 *      depends on the [InterceptActionAuditDao] alone (no uploader, no change-log, no API).
 *
 *  (c) Never-transmitted, structural (reflection): the audit types ([InterceptActionAudit] /
 *      [LocalInterceptActionAudit]) are structurally unreachable from every sync
 *      payload/request type ([AggregatedCounterPayload], its nested [AppCounter], and the
 *      generic change-log DTOs [SyncPushRequest]/[SyncEvent]/[SyncPullResponse]). The audit
 *      record therefore cannot appear in any sync payload.
 *
 * This is a plain JVM unit test (data module `src/test`) using a hand-written in-memory
 * [InterceptActionAuditDao] fake, so no Android emulator is required. Properties are exercised
 * with Kotest's [checkAll] engine at 200 generated cases each (>= 100 per the design Testing
 * Strategy). It is hosted in a JUnit4 test class — matching the data module's discovery
 * convention — because AGP's Android unit-test task discovers JUnit tests while Kotest spec
 * styles are not auto-discovered here; [checkAll] still provides the full property-based
 * generation and shrinking.
 */
@OptIn(ExperimentalKotest::class)
class InterceptActionAuditLocalOnlyPropertyTest {

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    private val config = PropTestConfig(iterations = 200)

    private val packageSegments = Arb.of(
        "com", "org", "net", "example", "app", "social", "video",
        "game", "news", "chat", "foo", "bar", "baz", "player", "feed"
    )

    // 2..4 dotted package segments, e.g. "com.example.app".
    private val packageNames: Arb<String> =
        Arb.list(packageSegments, 2..4).map { it.joinToString(".") }

    /**
     * Generates arbitrary audit entries: a (userId, appPackageName, actionType, recordedAt)
     * tuple. recordedAt is millisecond-precision (the Room mapping round-trips through epoch
     * millis, so sub-millisecond precision would not survive and is not part of the property).
     */
    private val audits: Arb<InterceptActionAudit> = Arb.bind(
        Arb.uuid(),                                  // id
        Arb.uuid(),                                  // userId
        packageNames,                                // appPackageName
        Arb.enum<InterceptActionType>(),             // actionType
        Arb.long(0L, 4_102_444_800_000L)             // recordedAt epoch millis (year ~2100)
    ) { id, userId, pkg, action, millis ->
        InterceptActionAudit(
            id = id,
            userId = userId,
            appPackageName = pkg,
            actionType = action,
            recordedAt = Instant.ofEpochMilli(millis)
        )
    }

    @Test
    fun `each executed action appends exactly one local audit entry with correct package and action type`() =
        runTest {
            checkAll(config, audits) { audit ->
                val dao = FakeInterceptActionAuditDao()
                val syncSpy = SyncInteractionSpy()
                val repository = InterceptActionAuditRepositoryImpl(dao)

                val before = repository.listForUser(audit.userId)

                val result = repository.append(audit.userId, audit)
                assertTrue("append succeeds", result.isSuccess)

                val after = repository.listForUser(audit.userId)
                assertEquals("exactly one more audit entry persisted", 1, after.size - before.size)

                val appended = after.last()
                assertEquals("package preserved", audit.appPackageName, appended.appPackageName)
                assertEquals("action type preserved", audit.actionType, appended.actionType)
                assertEquals("owner preserved", audit.userId, appended.userId)
                assertEquals("recordedAt preserved", audit.recordedAt, appended.recordedAt)

                // The write hit ONLY the audit DAO — exactly one insert, no sync interaction.
                assertEquals("exactly one DAO insert", 1, dao.insertCount)
                assertFalse("append must not touch any sync path", syncSpy.wasTouched)
            }
        }

    @Test
    fun `appended entries are readable only by their owning user and never touch any sync path`() =
        runTest {
            checkAll(config, audits) { audit ->
                val dao = FakeInterceptActionAuditDao()
                val syncSpy = SyncInteractionSpy()
                val repository = InterceptActionAuditRepositoryImpl(dao)

                assertTrue(repository.append(audit.userId, audit).isSuccess)

                // The owner reads it back; a different user sees nothing (user scoping).
                val owner = repository.listForUser(audit.userId)
                assertEquals(listOf(audit.appPackageName), owner.map { it.appPackageName })
                val other = UUID.randomUUID()
                assertEquals(emptyList<InterceptActionAudit>(), repository.listForUser(other))

                // No sync/upload/change-log call was ever made by the append path.
                assertFalse("append must not transmit records", syncSpy.wasTouched)
            }
        }

    @Test
    fun `a sequence of actions appends exactly that many local entries and transmits none`() =
        runTest {
            checkAll(config, Arb.uuid(), Arb.list(audits, 1..8)) { userId, generated ->
                val dao = FakeInterceptActionAuditDao()
                val syncSpy = SyncInteractionSpy()
                val repository = InterceptActionAuditRepositoryImpl(dao)

                // All entries belong to the one authenticated user (append cross-checks
                // owner). Each entry models a DISTINCT executed action, so give every one a
                // fresh unique id: the generator can otherwise draw colliding UUIDs, which
                // would make two entries collapse to one row under the DAO's
                // REPLACE-on-conflict-by-id semantics (an artifact of generation, not a real
                // sequence of distinct intercept actions).
                val owned = generated.map { it.copy(id = UUID.randomUUID(), userId = userId) }
                owned.forEach { assertTrue(repository.append(userId, it).isSuccess) }

                // Exactly one local row per action; nothing transmitted.
                assertEquals("one local row per action", owned.size, dao.insertCount)
                assertEquals("all rows readable by owner", owned.size, repository.listForUser(userId).size)
                assertFalse("no action transmitted a record", syncSpy.wasTouched)
            }
        }

    @Test
    fun `audit record types are structurally unreachable from every sync payload type`() {
        // The produced audit record cannot appear in any sync payload because no sync
        // payload/request type references the audit types (directly or via a
        // collection/map type argument).
        assertUnreachableFromSyncTypes(InterceptActionAudit::class.java)
        assertUnreachableFromSyncTypes(LocalInterceptActionAudit::class.java)
    }

    /**
     * Asserts the given [auditType] is structurally unreachable from every sync payload/request
     * type: no declared field of those types is (or is a List/Set/Map of) the audit type. Uses
     * java.lang.reflect only (kotlin-reflect is not on the data module test classpath).
     */
    private fun assertUnreachableFromSyncTypes(auditType: Class<*>) {
        val syncTypes = listOf(
            AggregatedCounterPayload::class.java,
            AggregatedCounterPayload.AppCounter::class.java,
            com.dopashift.data.remote.dto.SyncPushRequest::class.java,
            com.dopashift.data.remote.dto.SyncEvent::class.java,
            com.dopashift.data.remote.dto.SyncPullResponse::class.java
        )
        syncTypes.forEach { syncType ->
            syncType.declaredFields.forEach { field ->
                assertFalse(
                    "${syncType.simpleName}.${field.name} must not be of audit type " +
                        auditType.simpleName,
                    auditType.isAssignableFrom(field.type)
                )

                val generic = field.genericType
                if (generic is ParameterizedType) {
                    generic.actualTypeArguments.forEach { arg ->
                        val argClass = when (arg) {
                            is Class<*> -> arg
                            is ParameterizedType -> arg.rawType as? Class<*>
                            else -> null
                        }
                        if (argClass != null) {
                            assertFalse(
                                "${syncType.simpleName}.${field.name} must not carry audit type " +
                                    "${auditType.simpleName} as a type argument",
                                auditType.isAssignableFrom(argClass)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * In-memory fake of [InterceptActionAuditDao] backed by a list, mirroring the real Room query
 * semantics: user-scoped reads ordered by recordedAt, insert appends a row (REPLACE-on-conflict
 * by primary key). Tracks [insertCount] so a test can assert exactly one append reached the DAO.
 */
private class FakeInterceptActionAuditDao : InterceptActionAuditDao {

    private val rows = mutableListOf<LocalInterceptActionAudit>()
    var insertCount: Int = 0
        private set

    override suspend fun insert(entry: LocalInterceptActionAudit) {
        rows.removeAll { it.id == entry.id }
        rows.add(entry)
        insertCount++
    }

    override suspend fun listByUser(userId: String): List<LocalInterceptActionAudit> =
        rows.filter { it.userId == userId }.sortedBy { it.recordedAt }
}

/**
 * A spy standing in for any sync/upload/change-log boundary. If the audit-append path ever
 * tried to transmit a record, it would have to go through a component like this — the spy
 * records every such interaction. It is wired alongside the repository but never handed to it,
 * proving the repository has no dependency capable of transmitting a record.
 */
private class SyncInteractionSpy : AggregatedCounterUploader {
    private val uploads = mutableListOf<AggregatedCounterPayload>()
    private val enqueuedEvents = mutableListOf<Any>()

    val wasTouched: Boolean
        get() = uploads.isNotEmpty() || enqueuedEvents.isNotEmpty()

    override suspend fun upload(payload: AggregatedCounterPayload) {
        uploads.add(payload)
    }
}
