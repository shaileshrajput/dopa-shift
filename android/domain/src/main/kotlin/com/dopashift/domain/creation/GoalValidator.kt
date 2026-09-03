package com.dopashift.domain.creation

/**
 * Shared, framework-free validator for goal creation input (DQC-2.1, DQC-2.6, DQC-2.7).
 *
 * This is the single usability layer used by BOTH the Dashboard creation flow and the
 * dedicated Goals screen, so client-side validation is identical everywhere and never a
 * parallel implementation (DQC-1.10, DQC-1.11). It mirrors — but never replaces — the
 * server-side validation the Backend re-runs on every field (DQC-5.5).
 *
 * The rules enforced (matching parent Requirement 1 AC1-AC2 and DQC-2.1):
 * - name: 1-[NAME_MAX] characters and unique per user, compared case-insensitively.
 * - category: 1-[CATEGORY_MAX] characters.
 * - keywords: 1-[KEYWORDS_MAX] entries, each 1-[KEYWORD_MAX_LEN] characters.
 *
 * [validate] returns a per-field error map keyed by field name; an empty map means the
 * input is valid. The map feeds [CreationResult.ValidationError] and inline field errors
 * in the UI (DQC-2.6, DQC-2.7).
 */
object GoalValidator {

    /** Maximum length of a goal name in characters (DQC-2.1). */
    const val NAME_MAX = 100

    /** Maximum length of a goal category in characters (DQC-2.1). */
    const val CATEGORY_MAX = 50

    /** Maximum length of a single keyword in characters (DQC-2.1). */
    const val KEYWORD_MAX_LEN = 50

    /** Maximum number of keywords a goal may have (DQC-2.1). */
    const val KEYWORDS_MAX = 20

    /** Field key for name errors in the returned error map. */
    const val FIELD_NAME = "name"

    /** Field key for category errors in the returned error map. */
    const val FIELD_CATEGORY = "category"

    /** Field key for keyword errors in the returned error map. */
    const val FIELD_KEYWORDS = "keywords"

    /**
     * Validate goal creation input, returning a per-field error map (DQC-2.1, DQC-2.6, DQC-2.7).
     *
     * The name is trimmed before both length and uniqueness checks so leading/trailing
     * whitespace is not counted toward the bound and does not defeat duplicate detection.
     * Uniqueness is case-insensitive: the trimmed, lower-cased name must not appear in
     * [existingNamesLower]. Each keyword is checked individually for length; the first
     * offending keyword produces the keyword-field error.
     *
     * @param name the proposed goal name.
     * @param category the proposed goal category.
     * @param keywords the proposed keywords.
     * @param existingNamesLower the user's existing goal names, already lower-cased, used
     *   for case-insensitive uniqueness (every query is scoped by the authenticated user).
     * @return a map of field key ([FIELD_NAME], [FIELD_CATEGORY], [FIELD_KEYWORDS]) to a
     *   human-readable error message; empty when the input is valid.
     */
    fun validate(
        name: String,
        category: String,
        keywords: List<String>,
        existingNamesLower: Set<String>
    ): Map<String, String> {
        val errors = LinkedHashMap<String, String>()

        val trimmedName = name.trim()
        when {
            trimmedName.isEmpty() ->
                errors[FIELD_NAME] = "Name is required."
            trimmedName.length > NAME_MAX ->
                errors[FIELD_NAME] = "Name must be at most $NAME_MAX characters."
            existingNamesLower.contains(trimmedName.lowercase()) ->
                errors[FIELD_NAME] = "You already have a goal with this name."
        }

        val trimmedCategory = category.trim()
        when {
            trimmedCategory.isEmpty() ->
                errors[FIELD_CATEGORY] = "Category is required."
            trimmedCategory.length > CATEGORY_MAX ->
                errors[FIELD_CATEGORY] = "Category must be at most $CATEGORY_MAX characters."
        }

        when {
            keywords.isEmpty() ->
                errors[FIELD_KEYWORDS] = "Add at least one keyword."
            keywords.size > KEYWORDS_MAX ->
                errors[FIELD_KEYWORDS] = "Use at most $KEYWORDS_MAX keywords."
            else -> {
                val invalid = keywords.firstOrNull { it.trim().isEmpty() || it.trim().length > KEYWORD_MAX_LEN }
                if (invalid != null) {
                    errors[FIELD_KEYWORDS] =
                        "Each keyword must be 1 to $KEYWORD_MAX_LEN characters."
                }
            }
        }

        return errors
    }
}
