package com.dopashift.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented migration test for [DopaShiftDatabase].
 *
 * Validates:
 *  - MIGRATION_1_2 (Requirement 3.5): preserves existing `interception_rules` rows and adds
 *    `pausedForDate` as a nullable column defaulting to NULL for pre-existing rows.
 *  - MIGRATION_2_3: creates the local-only `quick_create_diagnostics` table.
 *  - MIGRATION_3_4 (Requirements 1.7, 3.4): preserves existing `interception_rules` rows,
 *    defaults `limitType` to `ONCE`, adds `repetitiveIntervalMinutes` as a nullable column,
 *    and creates the local-only `intercept_action_audit` table.
 *
 * No exported v1 schema JSON exists (schema export was enabled at v2), so the v1 table
 * is created manually via [SupportSQLiteDatabase] before running the migration. From v2
 * onward, exported schema JSONs under `schemas/` are used to create/validate each version.
 */
@RunWith(AndroidJUnit4::class)
class DopaShiftDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DopaShiftDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesExistingRows_andAddsNullablePausedForDate() {
        // Create the v1 `interception_rules` table manually with the v1 column set,
        // then insert a representative pre-existing row.
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `interception_rules` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `goalId` TEXT,
                    `appPackageName` TEXT,
                    `siteDomain` TEXT,
                    `dailyAllowanceMinutes` INTEGER NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_interception_rules_userId` ON `interception_rules` (`userId`)")
            execSQL(
                """
                INSERT INTO `interception_rules`
                    (`id`, `userId`, `goalId`, `appPackageName`, `siteDomain`, `dailyAllowanceMinutes`, `isActive`, `createdAt`)
                VALUES
                    ('rule-1', 'user-1', 'goal-1', 'com.example.distraction', NULL, 30, 1, 1710000000000)
                """.trimIndent()
            )
            close()
        }

        // Run the migration. validateDroppedTables = true makes Room validate the resulting
        // schema against the exported v2 schema, confirming the column was added correctly.
        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT * FROM `interception_rules`").use { cursor ->
            // The pre-existing row is preserved.
            assertEquals("Migrated table should retain exactly one row", 1, cursor.count)
            assertTrue(cursor.moveToFirst())

            // Original column values are unchanged.
            assertEquals("rule-1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("user-1", cursor.getString(cursor.getColumnIndexOrThrow("userId")))
            assertEquals("goal-1", cursor.getString(cursor.getColumnIndexOrThrow("goalId")))
            assertEquals(
                "com.example.distraction",
                cursor.getString(cursor.getColumnIndexOrThrow("appPackageName"))
            )
            assertEquals(30, cursor.getInt(cursor.getColumnIndexOrThrow("dailyAllowanceMinutes")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isActive")))
            assertEquals(1710000000000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))

            // The new column exists and is NULL for the migrated (pre-existing) row.
            val pausedForDateIndex = cursor.getColumnIndex("pausedForDate")
            assertTrue("pausedForDate column should exist after migration", pausedForDateIndex >= 0)
            assertTrue(
                "pausedForDate should be NULL for migrated rows",
                cursor.isNull(pausedForDateIndex)
            )
        }
    }

    @Test
    fun migrate1To2_allowsNullAndNonNullPausedForDate() {
        // Confirm the new column is nullable: a post-migration insert can supply NULL,
        // and an update can set a non-null ISO LocalDate value.
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `interception_rules` (
                    `id` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `goalId` TEXT,
                    `appPackageName` TEXT,
                    `siteDomain` TEXT,
                    `dailyAllowanceMinutes` INTEGER NOT NULL,
                    `isActive` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_interception_rules_userId` ON `interception_rules` (`userId`)")
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        // Insert without pausedForDate (nullable => NULL).
        db.execSQL(
            """
            INSERT INTO `interception_rules`
                (`id`, `userId`, `goalId`, `appPackageName`, `siteDomain`, `dailyAllowanceMinutes`, `isActive`, `createdAt`)
            VALUES
                ('rule-2', 'user-1', NULL, 'com.example.app', NULL, 60, 1, 1710000001000)
            """.trimIndent()
        )
        // Set a non-null paused date on another row.
        db.execSQL(
            """
            INSERT INTO `interception_rules`
                (`id`, `userId`, `goalId`, `appPackageName`, `siteDomain`, `dailyAllowanceMinutes`, `isActive`, `createdAt`, `pausedForDate`)
            VALUES
                ('rule-3', 'user-1', NULL, 'com.example.other', NULL, 45, 1, 1710000002000, '2024-03-10')
            """.trimIndent()
        )

        db.query("SELECT `id`, `pausedForDate` FROM `interception_rules` ORDER BY `id`").use { cursor ->
            assertEquals(2, cursor.count)

            assertTrue(cursor.moveToFirst())
            assertEquals("rule-2", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("pausedForDate")))

            assertTrue(cursor.moveToNext())
            assertEquals("rule-3", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("2024-03-10", cursor.getString(cursor.getColumnIndexOrThrow("pausedForDate")))
        }
    }

    @Test
    fun migrate2To3_createsQuickCreateDiagnosticsTable_andAcceptsRows() {
        // Start from a v2 database validated by Room against the exported v2 schema, then
        // apply MIGRATION_2_3 which adds the local-only `quick_create_diagnostics` table.
        helper.createDatabase(TEST_DB, 2).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        // A row with a non-null entityType and one with a NULL entityType are both accepted.
        db.execSQL(
            """
            INSERT INTO `quick_create_diagnostics`
                (`id`, `eventType`, `correlationId`, `entityType`, `occurredAt`)
            VALUES
                ('evt-1', 'ENTITY_CREATED', 'corr-1', 'GOAL', 1710000000000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `quick_create_diagnostics`
                (`id`, `eventType`, `correlationId`, `entityType`, `occurredAt`)
            VALUES
                ('evt-2', 'QUICK_CREATE_OPENED', 'corr-1', NULL, 1710000001000)
            """.trimIndent()
        )

        db.query("SELECT * FROM `quick_create_diagnostics` ORDER BY `occurredAt` ASC").use { cursor ->
            assertEquals(2, cursor.count)

            assertTrue(cursor.moveToFirst())
            assertEquals("evt-1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("ENTITY_CREATED", cursor.getString(cursor.getColumnIndexOrThrow("eventType")))
            assertEquals("corr-1", cursor.getString(cursor.getColumnIndexOrThrow("correlationId")))
            assertEquals("GOAL", cursor.getString(cursor.getColumnIndexOrThrow("entityType")))
            assertEquals(1710000000000L, cursor.getLong(cursor.getColumnIndexOrThrow("occurredAt")))

            assertTrue(cursor.moveToNext())
            assertEquals("evt-2", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("entityType")))
        }
    }

    @Test
    fun migrate3To4_preservesExistingRows_defaultsLimitTypeToOnce_andAddsNullableInterval() {
        // Start from a v3 database validated by Room against the exported v3 schema. This
        // creates the full v3 `interception_rules` table (including `pausedForDate`), then
        // insert a representative pre-v4 row (no limitType / repetitiveIntervalMinutes columns).
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO `interception_rules`
                    (`id`, `userId`, `goalId`, `appPackageName`, `siteDomain`, `dailyAllowanceMinutes`, `isActive`, `createdAt`, `pausedForDate`)
                VALUES
                    ('rule-1', 'user-1', 'goal-1', 'com.example.distraction', NULL, 30, 1, 1710000000000, NULL)
                """.trimIndent()
            )
            close()
        }

        // Apply the full migration chain up to v4. validateDroppedTables = true makes Room
        // validate the resulting schema against the exported v4 schema, confirming both the
        // new columns and the new `intercept_action_audit` table were created correctly.
        val db = helper.runMigrationsAndValidate(
            TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4
        )

        db.query("SELECT * FROM `interception_rules`").use { cursor ->
            // The pre-existing row is preserved (Req 1.7).
            assertEquals("Migrated table should retain exactly one row", 1, cursor.count)
            assertTrue(cursor.moveToFirst())

            // Original column values are unchanged.
            assertEquals("rule-1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("user-1", cursor.getString(cursor.getColumnIndexOrThrow("userId")))
            assertEquals("goal-1", cursor.getString(cursor.getColumnIndexOrThrow("goalId")))
            assertEquals(
                "com.example.distraction",
                cursor.getString(cursor.getColumnIndexOrThrow("appPackageName"))
            )
            assertEquals(30, cursor.getInt(cursor.getColumnIndexOrThrow("dailyAllowanceMinutes")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isActive")))
            assertEquals(1710000000000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))

            // limitType defaults to 'ONCE' for the migrated (pre-existing) row (Req 1.7).
            val limitTypeIndex = cursor.getColumnIndex("limitType")
            assertTrue("limitType column should exist after migration", limitTypeIndex >= 0)
            assertEquals(
                "limitType should default to ONCE for migrated rows",
                "ONCE",
                cursor.getString(limitTypeIndex)
            )

            // repetitiveIntervalMinutes is added as a nullable column, NULL for migrated rows.
            val intervalIndex = cursor.getColumnIndex("repetitiveIntervalMinutes")
            assertTrue(
                "repetitiveIntervalMinutes column should exist after migration",
                intervalIndex >= 0
            )
            assertTrue(
                "repetitiveIntervalMinutes should be NULL for migrated rows",
                cursor.isNull(intervalIndex)
            )
        }
    }

    @Test
    fun migrate3To4_allowsOnceAndRepetitiveRules_withNullAndNonNullInterval() {
        // Confirm the new columns behave as specified after migration: ONCE rows keep a NULL
        // interval, and REPETITIVE rows can store a whole-minute interval (1..120).
        helper.createDatabase(TEST_DB, 3).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4
        )

        // Insert without limitType/interval => defaults ONCE with NULL interval.
        db.execSQL(
            """
            INSERT INTO `interception_rules`
                (`id`, `userId`, `goalId`, `appPackageName`, `siteDomain`, `dailyAllowanceMinutes`, `isActive`, `createdAt`)
            VALUES
                ('rule-once', 'user-1', NULL, 'com.example.app', NULL, 60, 1, 1710000001000)
            """.trimIndent()
        )
        // Insert an explicit REPETITIVE rule with a valid interval.
        db.execSQL(
            """
            INSERT INTO `interception_rules`
                (`id`, `userId`, `goalId`, `appPackageName`, `siteDomain`, `dailyAllowanceMinutes`, `isActive`, `createdAt`, `limitType`, `repetitiveIntervalMinutes`)
            VALUES
                ('rule-rep', 'user-1', NULL, 'com.example.other', NULL, 45, 1, 1710000002000, 'REPETITIVE', 15)
            """.trimIndent()
        )

        db.query(
            "SELECT `id`, `limitType`, `repetitiveIntervalMinutes` FROM `interception_rules` ORDER BY `id`"
        ).use { cursor ->
            assertEquals(2, cursor.count)

            // 'rule-once' sorts before 'rule-rep'.
            assertTrue(cursor.moveToFirst())
            assertEquals("rule-once", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("ONCE", cursor.getString(cursor.getColumnIndexOrThrow("limitType")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("repetitiveIntervalMinutes")))

            assertTrue(cursor.moveToNext())
            assertEquals("rule-rep", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("REPETITIVE", cursor.getString(cursor.getColumnIndexOrThrow("limitType")))
            assertEquals(15, cursor.getInt(cursor.getColumnIndexOrThrow("repetitiveIntervalMinutes")))
        }
    }

    @Test
    fun migrate3To4_createsInterceptActionAuditTable_andAcceptsUserScopedRows() {
        // Start from a v3 database and apply the migration chain up to v4, which creates the
        // local-only `intercept_action_audit` table (Req 3.4).
        helper.createDatabase(TEST_DB, 3).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4
        )

        // The table exists (Req 3.4): confirm via sqlite_master.
        db.query(
            "SELECT `name` FROM `sqlite_master` WHERE `type` = 'table' AND `name` = 'intercept_action_audit'"
        ).use { cursor ->
            assertEquals(
                "intercept_action_audit table should exist after migration",
                1,
                cursor.count
            )
        }

        // The table accepts both action types, scoped to a userId.
        db.execSQL(
            """
            INSERT INTO `intercept_action_audit`
                (`id`, `userId`, `appPackageName`, `actionType`, `recordedAt`)
            VALUES
                ('audit-1', 'user-1', 'com.example.distraction', 'CONTINUE', 1710000000000)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `intercept_action_audit`
                (`id`, `userId`, `appPackageName`, `actionType`, `recordedAt`)
            VALUES
                ('audit-2', 'user-1', 'com.example.distraction', 'SWITCH_TO_DOPASHIFT', 1710000001000)
            """.trimIndent()
        )

        db.query(
            "SELECT * FROM `intercept_action_audit` WHERE `userId` = 'user-1' ORDER BY `recordedAt` ASC"
        ).use { cursor ->
            assertEquals(2, cursor.count)

            assertTrue(cursor.moveToFirst())
            assertEquals("audit-1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("user-1", cursor.getString(cursor.getColumnIndexOrThrow("userId")))
            assertEquals(
                "com.example.distraction",
                cursor.getString(cursor.getColumnIndexOrThrow("appPackageName"))
            )
            assertEquals("CONTINUE", cursor.getString(cursor.getColumnIndexOrThrow("actionType")))
            assertEquals(1710000000000L, cursor.getLong(cursor.getColumnIndexOrThrow("recordedAt")))

            assertTrue(cursor.moveToNext())
            assertEquals("audit-2", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals(
                "SWITCH_TO_DOPASHIFT",
                cursor.getString(cursor.getColumnIndexOrThrow("actionType"))
            )
        }
    }

    private companion object {
        const val TEST_DB = "migration-test-db"
    }
}
