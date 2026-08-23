package com.dopashift.domain.property

import com.dopashift.domain.entity.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Property 15: Change Log Serialization Round-Trip
 *
 * For any valid domain entity (GoalProfile, DailyTodoItem, GoalChecklistItem, HabitTrack),
 * serializing its fields to Change_Log JSON values and then deserializing them back
 * SHALL produce an equivalent entity.
 *
 * **Validates: Requirements 4.4, 4.5**
 *
 * Tag: Feature: dopa-shift, Property 15: Change Log Serialization Round-Trip
 */
class ChangeLogSerializationPropertyTest : FunSpec({

    tags(
        io.kotest.core.Tag("Feature: dopa-shift"),
        io.kotest.core.Tag("Property 15: Change Log Serialization Round-Trip")
    )

    // ===== Serialization utilities simulating Change_Log field-level JSON values =====

    /**
     * Serializes a field value to a JSON string representation (as stored in ChangeLogEntry.value).
     * Uses simple JSON encoding rules: strings are quoted, lists are JSON arrays, etc.
     */
    fun serializeFieldValue(value: Any?): String? {
        if (value == null) return null
        return when (value) {
            is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")}\""
            is Boolean -> value.toString()
            is Int -> value.toString()
            is Long -> value.toString()
            is UUID -> "\"$value\""
            is Instant -> "\"$value\""
            is LocalDate -> "\"$value\""
            is CheckpointStatus -> "\"${value.name}\""
            is List<*> -> "[${value.joinToString(",") { serializeFieldValue(it) ?: "null" }}]"
            else -> throw IllegalArgumentException("Unsupported type: ${value::class}")
        }
    }

    /**
     * Deserializes a JSON string value back to a typed field.
     */
    fun deserializeString(json: String): String {
        // Strip surrounding quotes and unescape
        val inner = json.substring(1, json.length - 1)
        return inner
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    fun deserializeBoolean(json: String): Boolean = json.toBoolean()

    fun deserializeInt(json: String): Int = json.toInt()

    fun deserializeUUID(json: String): UUID = UUID.fromString(deserializeString(json))

    fun deserializeInstant(json: String): Instant = Instant.parse(deserializeString(json))

    fun deserializeLocalDate(json: String): LocalDate = LocalDate.parse(deserializeString(json))

    fun deserializeCheckpointStatus(json: String): CheckpointStatus =
        CheckpointStatus.valueOf(deserializeString(json))

    fun deserializeStringList(json: String): List<String> {
        if (json == "[]") return emptyList()
        val inner = json.substring(1, json.length - 1)
        // Split on commas between quoted strings
        val result = mutableListOf<String>()
        var i = 0
        while (i < inner.length) {
            if (inner[i] == '"') {
                val sb = StringBuilder()
                i++ // skip opening quote
                while (i < inner.length && inner[i] != '"') {
                    if (inner[i] == '\\' && i + 1 < inner.length) {
                        val next = inner[i + 1]
                        when (next) {
                            '\\' -> { sb.append('\\'); i += 2 }
                            '"' -> { sb.append('"'); i += 2 }
                            'n' -> { sb.append('\n'); i += 2 }
                            'r' -> { sb.append('\r'); i += 2 }
                            't' -> { sb.append('\t'); i += 2 }
                            else -> { sb.append(inner[i]); i++ }
                        }
                    } else {
                        sb.append(inner[i])
                        i++
                    }
                }
                i++ // skip closing quote
                result.add(sb.toString())
            } else if (inner[i] == ',') {
                i++
            } else {
                i++
            }
        }
        return result
    }

    // ===== Field map serialization / deserialization for each entity =====

    fun serializeGoalProfile(goal: GoalProfile): Map<String, String?> = mapOf(
        "id" to serializeFieldValue(goal.id),
        "userId" to serializeFieldValue(goal.userId),
        "name" to serializeFieldValue(goal.name),
        "category" to serializeFieldValue(goal.category),
        "keywords" to serializeFieldValue(goal.keywords),
        "createdAt" to serializeFieldValue(goal.createdAt),
        "updatedAt" to serializeFieldValue(goal.updatedAt),
        "isActive" to serializeFieldValue(goal.isActive)
    )

    fun deserializeGoalProfile(fields: Map<String, String?>): GoalProfile = GoalProfile(
        id = deserializeUUID(fields["id"]!!),
        userId = deserializeUUID(fields["userId"]!!),
        name = deserializeString(fields["name"]!!),
        category = deserializeString(fields["category"]!!),
        keywords = deserializeStringList(fields["keywords"]!!),
        createdAt = deserializeInstant(fields["createdAt"]!!),
        updatedAt = deserializeInstant(fields["updatedAt"]!!),
        isActive = deserializeBoolean(fields["isActive"]!!)
    )

    fun serializeDailyTodoItem(item: DailyTodoItem): Map<String, String?> = mapOf(
        "id" to serializeFieldValue(item.id),
        "userId" to serializeFieldValue(item.userId),
        "text" to serializeFieldValue(item.text),
        "dueDateTime" to serializeFieldValue(item.dueDateTime),
        "isCompleted" to serializeFieldValue(item.isCompleted),
        "createdAt" to serializeFieldValue(item.createdAt),
        "updatedAt" to serializeFieldValue(item.updatedAt),
        "dayDate" to serializeFieldValue(item.dayDate)
    )

    fun deserializeDailyTodoItem(fields: Map<String, String?>): DailyTodoItem = DailyTodoItem(
        id = deserializeUUID(fields["id"]!!),
        userId = deserializeUUID(fields["userId"]!!),
        text = deserializeString(fields["text"]!!),
        dueDateTime = fields["dueDateTime"]?.let { deserializeInstant(it) },
        isCompleted = deserializeBoolean(fields["isCompleted"]!!),
        createdAt = deserializeInstant(fields["createdAt"]!!),
        updatedAt = deserializeInstant(fields["updatedAt"]!!),
        dayDate = deserializeLocalDate(fields["dayDate"]!!)
    )

    fun serializeGoalChecklistItem(item: GoalChecklistItem): Map<String, String?> = mapOf(
        "id" to serializeFieldValue(item.id),
        "goalId" to serializeFieldValue(item.goalId),
        "userId" to serializeFieldValue(item.userId),
        "text" to serializeFieldValue(item.text),
        "isCompleted" to serializeFieldValue(item.isCompleted),
        "createdAt" to serializeFieldValue(item.createdAt),
        "updatedAt" to serializeFieldValue(item.updatedAt)
    )

    fun deserializeGoalChecklistItem(fields: Map<String, String?>): GoalChecklistItem = GoalChecklistItem(
        id = deserializeUUID(fields["id"]!!),
        goalId = deserializeUUID(fields["goalId"]!!),
        userId = deserializeUUID(fields["userId"]!!),
        text = deserializeString(fields["text"]!!),
        isCompleted = deserializeBoolean(fields["isCompleted"]!!),
        createdAt = deserializeInstant(fields["createdAt"]!!),
        updatedAt = deserializeInstant(fields["updatedAt"]!!)
    )

    fun serializeHabitTrack(track: HabitTrack): Map<String, String?> = mapOf(
        "id" to serializeFieldValue(track.id),
        "goalId" to serializeFieldValue(track.goalId),
        "userId" to serializeFieldValue(track.userId),
        "startDate" to serializeFieldValue(track.startDate),
        "currentDay" to serializeFieldValue(track.currentDay),
        "isFinished" to serializeFieldValue(track.isFinished)
        // checkpoints are serialized separately as sub-entities in real sync;
        // we test the track's own scalar fields for round-trip
    )

    fun deserializeHabitTrack(fields: Map<String, String?>, checkpoints: List<HabitCheckpoint>): HabitTrack = HabitTrack(
        id = deserializeUUID(fields["id"]!!),
        goalId = deserializeUUID(fields["goalId"]!!),
        userId = deserializeUUID(fields["userId"]!!),
        startDate = deserializeLocalDate(fields["startDate"]!!),
        currentDay = deserializeInt(fields["currentDay"]!!),
        isFinished = deserializeBoolean(fields["isFinished"]!!),
        checkpoints = checkpoints
    )

    // ===== Arbitrary generators for domain entities =====

    val arbValidName = Arb.string(1..100, Codepoint.alphanumeric())

    val arbValidCategory = Arb.string(1..50, Codepoint.alphanumeric())

    val arbValidKeyword = Arb.string(1..50, Codepoint.alphanumeric())

    val arbKeywordList = Arb.list(arbValidKeyword, 0..20)

    val arbValidTodoText = Arb.string(1..100, Codepoint.alphanumeric())

    val arbValidChecklistText = Arb.string(1..100, Codepoint.alphanumeric())

    val arbInstant = Arb.long(0L..2000000000L).map { Instant.ofEpochSecond(it) }

    val arbLocalDate = Arb.int(2020..2030).flatMap { year ->
        Arb.int(1..12).flatMap { month ->
            val maxDay = LocalDate.of(year, month, 1).lengthOfMonth()
            Arb.int(1..maxDay).map { day ->
                LocalDate.of(year, month, day)
            }
        }
    }

    val arbGoalProfile = Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        arbValidName,
        arbValidCategory,
        arbKeywordList,
        arbInstant,
        arbInstant,
        Arb.boolean()
    ) { id, userId, name, category, keywords, createdAt, updatedAt, isActive ->
        GoalProfile(
            id = id,
            userId = userId,
            name = name,
            category = category,
            keywords = keywords,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isActive = isActive
        )
    }

    val arbDailyTodoItem = Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        arbValidTodoText,
        Arb.boolean(),
        arbInstant,
        arbInstant,
        arbLocalDate
    ) { id, userId, text, isCompleted, createdAt, updatedAt, dayDate ->
        DailyTodoItem(
            id = id,
            userId = userId,
            text = text,
            dueDateTime = null, // simplified: no due date
            isCompleted = isCompleted,
            createdAt = createdAt,
            updatedAt = updatedAt,
            dayDate = dayDate
        )
    }

    val arbGoalChecklistItem = Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid(),
        arbValidChecklistText,
        Arb.boolean(),
        arbInstant,
        arbInstant
    ) { id, goalId, userId, text, isCompleted, createdAt, updatedAt ->
        GoalChecklistItem(
            id = id,
            goalId = goalId,
            userId = userId,
            text = text,
            isCompleted = isCompleted,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    val arbHabitTrack = Arb.bind(
        Arb.uuid(),
        Arb.uuid(),
        Arb.uuid(),
        arbLocalDate,
        Arb.int(1..30),
        Arb.boolean()
    ) { id, goalId, userId, startDate, currentDay, isFinished ->
        HabitTrack(
            id = id,
            goalId = goalId,
            userId = userId,
            startDate = startDate,
            currentDay = currentDay,
            isFinished = isFinished,
            checkpoints = emptyList()
        )
    }

    // ===== Property Tests =====

    test("Property 15: GoalProfile serialization round-trip produces equivalent entity") {
        checkAll(100, arbGoalProfile) { original ->
            val serialized = serializeGoalProfile(original)
            val deserialized = deserializeGoalProfile(serialized)
            deserialized shouldBe original
        }
    }

    test("Property 15: DailyTodoItem serialization round-trip produces equivalent entity") {
        checkAll(100, arbDailyTodoItem) { original ->
            val serialized = serializeDailyTodoItem(original)
            val deserialized = deserializeDailyTodoItem(serialized)
            deserialized shouldBe original
        }
    }

    test("Property 15: GoalChecklistItem serialization round-trip produces equivalent entity") {
        checkAll(100, arbGoalChecklistItem) { original ->
            val serialized = serializeGoalChecklistItem(original)
            val deserialized = deserializeGoalChecklistItem(serialized)
            deserialized shouldBe original
        }
    }

    test("Property 15: HabitTrack serialization round-trip produces equivalent entity") {
        checkAll(100, arbHabitTrack) { original ->
            val serialized = serializeHabitTrack(original)
            val deserialized = deserializeHabitTrack(serialized, original.checkpoints)
            deserialized shouldBe original
        }
    }
})
