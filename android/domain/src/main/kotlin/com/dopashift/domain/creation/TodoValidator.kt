package com.dopashift.domain.creation

/**
 * Field-level validation for [com.dopashift.domain.entity.DailyTodoItem] creation
 * (DQC-4.1, DQC-4.5, DQC-4.6, DQC-4.7).
 *
 * This is the single validator used by both the Dashboard quick-add / full to-do sheet
 * and the dedicated Daily Tasks screen, so acceptance and rejection are identical
 * regardless of [CreationOrigin] (DQC-1.10). It is a pure-Kotlin usability layer that
 * mirrors the server-side rules; the Backend re-validates every field and enforces the
 * same bounds, so client validation never replaces server validation (DQC-1.11).
 *
 * Bounds are exposed as constants so the UI can drive character counters and disable
 * the save action without duplicating magic numbers.
 */
object TodoValidator {

    /** Maximum length of to-do text, in characters (DQC-4.5). */
    const val TEXT_MAX = 500

    /** Maximum number of to-dos a user may create for a single day (DQC-4.6). */
    const val DAILY_CAP = 100

    /** Field key for text errors in the returned error map. */
    const val FIELD_TEXT = "text"

    /** Field key for the daily-cap error in the returned error map. */
    const val FIELD_DAILY_CAP = "dailyCap"

    /**
     * Validate a to-do creation attempt, returning a per-field error map keyed by field
     * name. An empty map means the input is valid.
     *
     * Rules:
     * - Text must be non-blank: empty or whitespace-only text is rejected (DQC-4.7).
     * - Text must be at most [TEXT_MAX] characters (DQC-4.5).
     * - The day must not already hold [DAILY_CAP] items: when [todayCount] is at or above
     *   the cap, further creation for that day is rejected (DQC-4.6).
     *
     * @param text the entered to-do text.
     * @param todayCount how many [com.dopashift.domain.entity.DailyTodoItem]s already
     *   exist for the target day (in the user's configured time zone).
     * @return a map of field name to human-readable error message; empty when valid.
     */
    fun validate(text: String, todayCount: Int): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        if (text.isBlank()) {
            errors[FIELD_TEXT] = "To-do text must not be empty"
        } else if (text.length > TEXT_MAX) {
            errors[FIELD_TEXT] = "To-do text must be at most $TEXT_MAX characters"
        }

        if (todayCount >= DAILY_CAP) {
            errors[FIELD_DAILY_CAP] = "You have reached the daily limit of $DAILY_CAP to-dos"
        }

        return errors
    }
}
