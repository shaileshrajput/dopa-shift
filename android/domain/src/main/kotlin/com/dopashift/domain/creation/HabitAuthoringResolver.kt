package com.dopashift.domain.creation

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import java.time.LocalDate
import java.util.UUID

/**
 * Pure-Kotlin resolver that turns a chosen [HabitAuthoring] mode into a valid, ready-to-persist
 * [HabitTrack] carrying exactly [HabitTemplate.CHECKPOINT_COUNT] (30) [HabitCheckpoint]s
 * (DQC-3.6, DQC-3.9, DQC-3.10, DQC-3.11, DQC-3.12).
 *
 * This is the single place the three authoring modes converge:
 * - [HabitAuthoring.Template] loads the bundled 30-checkpoint template for the goal category
 *   via [HabitTemplateProvider] (offline, DQC-3.6).
 * - [HabitAuthoring.Llm] uses the LLM-generated descriptions (DQC-3.7); fallback-to-template
 *   on failure is the caller's responsibility (DQC-3.8) — by the time a list reaches here it is
 *   treated as authored content.
 * - [HabitAuthoring.Manual] uses user-authored descriptions and, when fewer than 30 are supplied,
 *   auto-fills the remaining days with the last authored description (DQC-3.9).
 *
 * Every resolved description is trimmed and clamped to 1-[HabitTemplate.DESCRIPTION_MAX] (200)
 * characters so the produced checkpoints always satisfy the [HabitCheckpoint] invariant. The
 * resolved [HabitTrack] has its start date set to today in the user's configured time zone and
 * its current-day pointer set to 1 (DQC-3.10). Today's date is supplied by the caller so this
 * type stays pure and deterministic (no clock/time-zone lookup in `domain`).
 *
 * Framework-free by design: it takes only domain types plus a [HabitTemplateProvider] port and id
 * suppliers, so it is exercised directly by the property tests for this feature (Properties 10, 11).
 */
class HabitAuthoringResolver(
    private val templateProvider: HabitTemplateProvider
) {

    /**
     * Resolve [authoring] into a [HabitResolution] for the given goal and user (DQC-3.6, DQC-3.9-3.12).
     *
     * Description sourcing by mode:
     * - Template: the provider's template for [category] (or its default) supplies 30 descriptions.
     * - Llm: the generated descriptions are used as-is (then normalized/padded to 30).
     * - Manual: authored descriptions are used, with days beyond the last authored one filled with
     *   the last authored description (DQC-3.9). An [HabitResolution.autoFilledDays] count > 0 tells
     *   the caller to disclose the auto-fill to the user before saving.
     *
     * The produced [HabitTrack] has `startDate == today` and `currentDay == 1` (DQC-3.10). When an
     * active track already exists for the goal ([existingActiveTrackPresent] is true), the result's
     * [HabitResolution.precedenceAdvisory] is populated so the caller can tell the user which track
     * takes precedence in the Intercept_Overlay before saving (DQC-3.11). [withReminder] is passed
     * through unchanged so the caller can offer — but not require — a daily checkpoint Reminder (DQC-3.12).
     *
     * @param authoring the chosen authoring mode.
     * @param category the goal category, used to resolve a bundled template in Template mode.
     * @param goalId the existing, saved goal this track belongs to (never a missing/unsaved id).
     * @param userId the authenticated owner.
     * @param today today's date in the user's configured time zone, supplied by the caller.
     * @param existingActiveTrackPresent whether an active track already exists for [goalId] (DQC-3.11).
     * @param withReminder whether a daily checkpoint Reminder was requested (DQC-3.12).
     * @param habitTrackId id for the new track; defaults to a random [UUID].
     * @param checkpointIdFactory supplies each checkpoint id (given its 1-based day number); defaults to random.
     * @return a [HabitResolution] wrapping the ready-to-persist track, auto-fill count, reminder
     *   flag, and optional precedence advisory.
     * @throws IllegalArgumentException if [authoring] carries no usable descriptions (e.g. empty
     *   Manual/Llm input), since a habit cannot be resolved from nothing.
     */
    fun resolve(
        authoring: HabitAuthoring,
        category: String,
        goalId: GoalId,
        userId: UserId,
        today: LocalDate,
        existingActiveTrackPresent: Boolean = false,
        withReminder: Boolean = false,
        habitTrackId: UUID = UUID.randomUUID(),
        checkpointIdFactory: (dayNumber: Int) -> UUID = { UUID.randomUUID() }
    ): HabitResolution {
        val sourced = sourceDescriptions(authoring, category)
        require(sourced.isNotEmpty()) {
            "Cannot resolve a habit from empty authoring input"
        }

        val normalized = normalizeToThirty(sourced)
        val autoFilledDays = (CHECKPOINT_COUNT - sourced.size).coerceAtLeast(0)

        val checkpoints = normalized.mapIndexed { index, description ->
            val dayNumber = index + 1
            HabitCheckpoint(
                id = checkpointIdFactory(dayNumber),
                habitTrackId = habitTrackId,
                dayNumber = dayNumber,
                description = description,
                status = CheckpointStatus.PENDING
            )
        }

        val track = HabitTrack(
            id = habitTrackId,
            goalId = goalId,
            userId = userId,
            startDate = today,
            currentDay = 1,
            isFinished = false,
            checkpoints = checkpoints
        )

        return HabitResolution(
            track = track,
            autoFilledDays = autoFilledDays,
            withReminder = withReminder,
            precedenceAdvisory = if (existingActiveTrackPresent) {
                PrecedenceAdvisory(goalId = goalId, newTrackTakesPrecedence = true)
            } else {
                null
            }
        )
    }

    /**
     * Pick the raw, pre-normalization description list for [authoring].
     * Template mode consults the [HabitTemplateProvider]; the other modes carry their own list.
     */
    private fun sourceDescriptions(authoring: HabitAuthoring, category: String): List<String> =
        when (authoring) {
            is HabitAuthoring.Template -> resolveTemplateDescriptions(authoring, category)
            is HabitAuthoring.Llm -> authoring.generated
            is HabitAuthoring.Manual -> authoring.authored
        }

    /**
     * Resolve the template's 30 descriptions, matching [HabitAuthoring.Template.templateId] against
     * the category's templates and falling back to [HabitTemplateProvider.defaultTemplate].
     */
    private fun resolveTemplateDescriptions(
        template: HabitAuthoring.Template,
        category: String
    ): List<String> {
        val candidates = templateProvider.templatesForCategory(category)
        val chosen = candidates.firstOrNull { it.id == template.templateId }
            ?: templateProvider.defaultTemplate(category)
        return chosen.checkpointDescriptions
    }

    /**
     * Produce exactly [CHECKPOINT_COUNT] clamped, non-blank descriptions from [sourced].
     *
     * Each entry is trimmed and clamped to 1-[DESCRIPTION_MAX] characters; blank entries are
     * replaced with the last usable non-blank description so no checkpoint violates the
     * [HabitCheckpoint] invariant. When [sourced] has fewer than 30 usable entries, the remaining
     * days repeat the last usable description (DQC-3.9); when it has more, it is truncated to 30.
     */
    private fun normalizeToThirty(sourced: List<String>): List<String> {
        val result = ArrayList<String>(CHECKPOINT_COUNT)
        var lastUsable: String? = null

        for (raw in sourced) {
            if (result.size == CHECKPOINT_COUNT) break
            val clamped = clampDescription(raw)
            val usable = clamped ?: lastUsable ?: continue
            result.add(usable)
            lastUsable = usable
        }

        // Auto-fill any remaining days with the last authored description (DQC-3.9).
        val fill = lastUsable
            ?: error("At least one usable checkpoint description is required")
        while (result.size < CHECKPOINT_COUNT) {
            result.add(fill)
        }

        return result
    }

    /**
     * Trim and clamp a raw description to the [HabitCheckpoint] bound, or return null if blank.
     */
    private fun clampDescription(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.length > DESCRIPTION_MAX) trimmed.substring(0, DESCRIPTION_MAX) else trimmed
    }

    private companion object {
        const val CHECKPOINT_COUNT = HabitTemplate.CHECKPOINT_COUNT
        const val DESCRIPTION_MAX = HabitTemplate.DESCRIPTION_MAX
    }
}

/**
 * The outcome of resolving a [HabitAuthoring] mode into a persistable habit (DQC-3.9-3.12).
 *
 * @property track the ready-to-persist [HabitTrack] with exactly 30 valid checkpoints,
 *   `startDate == today`, and `currentDay == 1` (DQC-3.6, DQC-3.10).
 * @property autoFilledDays how many trailing days were auto-filled with the last authored
 *   description; > 0 means the caller must disclose the auto-fill before saving (DQC-3.9).
 * @property withReminder whether a daily checkpoint Reminder was requested (offered, not required — DQC-3.12).
 * @property precedenceAdvisory non-null when an active track already exists for the goal, telling
 *   the caller to inform the user which track takes precedence before saving (DQC-3.11).
 */
data class HabitResolution(
    val track: HabitTrack,
    val autoFilledDays: Int,
    val withReminder: Boolean,
    val precedenceAdvisory: PrecedenceAdvisory? = null
) {
    init {
        require(autoFilledDays >= 0) { "autoFilledDays must not be negative" }
    }

    /** Whether the caller should disclose auto-fill of missing days to the user (DQC-3.9). */
    val requiresAutoFillDisclosure: Boolean get() = autoFilledDays > 0
}

/**
 * Advisory surfaced when a goal already has an active [HabitTrack] and the user is creating another
 * (DQC-3.11). It is informational only — the resolver does not block creation; the caller uses it to
 * tell the user which track will take precedence in the Intercept_Overlay before saving.
 *
 * @property goalId the goal that already has an active track.
 * @property newTrackTakesPrecedence whether the newly created track takes precedence (parent Req 6 AC5).
 */
data class PrecedenceAdvisory(
    val goalId: GoalId,
    val newTrackTakesPrecedence: Boolean
)
