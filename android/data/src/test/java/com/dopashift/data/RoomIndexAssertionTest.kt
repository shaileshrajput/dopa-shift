// Feature: dynamic-ui-experience, Task 18.3
// Validates: Requirements DUX-6.3
package com.dopashift.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * DUX-6.3: "THE Android_App SHALL index local Room queries on `user_id`, `goal_id`, and `date`
 * to avoid full-table scans on Dashboard and analytics rendering."
 *
 * Regression guard: if someone removes an @Index from a dashboard/analytics-backing Room entity,
 * these assertions fail.
 *
 * Why the exported schema instead of runtime reflection on @Entity?
 * androidx.room.@Entity / @Index carry CLASS (BINARY) retention, so they are NOT visible via
 * runtime reflection (getAnnotation returns null). The authoritative, deterministic source for the
 * indices that Room actually compiles is the exported schema JSON (ksp room.schemaLocation ->
 * $projectDir/schemas). This test parses that committed JSON and asserts the required indices exist
 * on the exact table/column combinations. This is dependency-free (no JSON library, no Robolectric)
 * and deterministic: it reads a checked-in file and does exact string matching on the CREATE INDEX
 * statements Room generates.
 *
 * Composite indices satisfy a single-column requirement: SQLite can use the leading column of a
 * composite index for that column's lookups, so an index on (userId, dayDate) covers userId.
 */
class RoomIndexAssertionTest {

    private val schemaJson: String by lazy { readLatestExportedSchema() }

    /**
     * Locates the latest exported Room schema JSON. build.gradle.kts sets
     * room.schemaLocation = "$projectDir/schemas", and the DB class is DopaShiftDatabase, so the
     * schemas live under schemas/com.dopashift.data.local.DopaShiftDatabase/<version>.json.
     * Unit tests run with the module directory (android/data) as the working directory.
     */
    private fun readLatestExportedSchema(): String {
        val schemaDir = File("schemas/com.dopashift.data.local.DopaShiftDatabase")
        assertTrue(
            "Exported Room schema directory not found at ${schemaDir.absolutePath}. " +
                "Ensure ksp room.schemaLocation export is enabled and schemas are committed.",
            schemaDir.isDirectory
        )
        val schemaFile = schemaDir.listFiles { f -> f.extension == "json" }
            ?.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: -1 }
        assertTrue(
            "No exported schema JSON found in ${schemaDir.absolutePath}",
            schemaFile != null
        )
        return schemaFile!!.readText()
    }

    /**
     * Asserts a Room CREATE INDEX statement exists for [table] whose column list starts with the
     * given [columns] (a leading-prefix match, so composite indices count). Room emits the exact
     * form: CREATE INDEX IF NOT EXISTS `index_<table>_<cols>` ON `${TABLE_NAME}` (`c1`, `c2`).
     * We match on the ON `${TABLE_NAME}` (`col`... fragment which uniquely identifies the columns.
     */
    private fun assertIndexed(table: String, vararg columns: String) {
        // Extract every CREATE INDEX ... createSql for this table by matching its index name prefix.
        val indexNamePrefix = "index_${table}_"
        val hasMatch = schemaJson
            .lineSequence()
            .filter { it.contains("\"createSql\"") && it.contains("CREATE INDEX") }
            .filter { it.contains("`$indexNamePrefix") }
            .any { line ->
                // The column list appears after ON `${TABLE_NAME}` ( ... )
                val onIdx = line.indexOf("ON `\${TABLE_NAME}` (")
                if (onIdx < 0) return@any false
                val cols = line.substring(onIdx)
                    .substringAfter("(")
                    .substringBefore(")")
                    .split(",")
                    .map { it.trim().trim('`') }
                // Leading-prefix match: index (c1, c2, ...) covers a query on c1 (and c1,c2).
                cols.size >= columns.size && cols.take(columns.size) == columns.toList()
            }
        assertTrue(
            "Table `$table` must have a Room index whose leading columns are " +
                "${columns.toList()} (DUX-6.3). None found in exported schema.",
            hasMatch
        )
    }

    @Test
    fun goals_areIndexedOnUserId() {
        // goals: userId
        assertIndexed("goals", "userId")
    }

    @Test
    fun dailyTodoItems_areIndexedOnUserId() {
        // daily_todo_items: userId (composite with dayDate is fine)
        assertIndexed("daily_todo_items", "userId")
    }

    @Test
    fun goalChecklistItems_areIndexedOnGoalIdAndUserId() {
        // goal_checklist_items: goalId and userId
        assertIndexed("goal_checklist_items", "goalId")
        assertIndexed("goal_checklist_items", "userId")
    }

    @Test
    fun habitTracks_areIndexedOnGoalIdAndUserId() {
        // habit_tracks: goalId and userId
        assertIndexed("habit_tracks", "goalId")
        assertIndexed("habit_tracks", "userId")
    }

    @Test
    fun changeLog_isIndexedOnUserId() {
        // change_log: userId (composite with timestamp is fine)
        assertIndexed("change_log", "userId")
    }

    @Test
    fun efficiencyScores_areIndexedOnUserId() {
        // efficiency_scores: userId (composite with scoreDate is fine)
        assertIndexed("efficiency_scores", "userId")
    }
}
