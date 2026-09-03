package com.dopashift.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migrations for [DopaShiftDatabase].
 *
 * Each migration is a small, forward-only schema delta registered in the database builder
 * (see the data DI module). Migrations preserve existing rows.
 */

/**
 * v1 -> v2: add the nullable `pausedForDate` column to `interception_rules`.
 *
 * A non-null ISO LocalDate string means the Rule is paused for that calendar day
 * (Requirement 3.5). Existing rows default to NULL (not paused).
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE interception_rules ADD COLUMN pausedForDate TEXT")
    }
}

/**
 * v2 -> v3: add the `quick_create_diagnostics` table for Dashboard quick-create diagnostics.
 *
 * Local-only structured events (never synced raw; DQC-6.4/6.5). Column types mirror
 * [com.dopashift.data.local.entity.LocalDiagnosticEvent]: TEXT ids/types, INTEGER epoch millis.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `quick_create_diagnostics` (
                `id` TEXT NOT NULL,
                `eventType` TEXT NOT NULL,
                `correlationId` TEXT NOT NULL,
                `entityType` TEXT,
                `occurredAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_quick_create_diagnostics_correlationId` " +
                "ON `quick_create_diagnostics` (`correlationId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_quick_create_diagnostics_eventType` " +
                "ON `quick_create_diagnostics` (`eventType`)"
        )
    }
}

/**
 * v3 -> v4: repetitive app-limit interception schema (feature `android-app-limit-repitative`).
 *
 * This is the target migration for the v4 schema-version bump. It adds the `Limit_Type`
 * dimension to `interception_rules`:
 *  - `limitType`: `ONCE` | `REPETITIVE`, NOT NULL, defaults to `ONCE` for existing rows (Req 1.7).
 *  - `repetitiveIntervalMinutes`: nullable INTEGER; null for `ONCE`, 1..120 for `REPETITIVE`.
 *
 * NOTE: Task 2.2 extends THIS SAME migration to also create the `intercept_action_audit`
 * table (do not bump the version again — append the CREATE TABLE here).
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE interception_rules ADD COLUMN limitType TEXT NOT NULL DEFAULT 'ONCE'"
        )
        db.execSQL(
            "ALTER TABLE interception_rules ADD COLUMN repetitiveIntervalMinutes INTEGER"
        )
        // Task 2.2: local-only intercept-action audit log (Req 3.4, 3.5). Column types
        // mirror [com.dopashift.data.local.entity.LocalInterceptActionAudit]:
        // TEXT ids/strings, INTEGER epoch millis. Never synced.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `intercept_action_audit` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `appPackageName` TEXT NOT NULL,
                `actionType` TEXT NOT NULL,
                `recordedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_intercept_action_audit_userId` " +
                "ON `intercept_action_audit` (`userId`)"
        )
    }
}
