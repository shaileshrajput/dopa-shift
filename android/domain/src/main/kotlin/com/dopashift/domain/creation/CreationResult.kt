package com.dopashift.domain.creation

/**
 * The outcome of a Dashboard creation attempt (DQC-1.10, DQC-2.11).
 *
 * A successful creation carries an [UndoToken] so the UI can offer a sync-safe Undo
 * (DQC-1.6, DQC-1.7). Failures are modelled explicitly so no user input is ever
 * silently discarded: client-side field errors, back-end/sync rejections that retain
 * the original input for correction (DQC-2.11), and unexpected failures are distinct.
 *
 * The type parameter is covariant so the input-independent variants can be declared
 * `CreationResult<Nothing>` and still satisfy any `CreationResult<T>` call site.
 *
 * @param T the created entity type on success ([GoalProfile], [HabitTrack], etc.).
 */
sealed interface CreationResult<out T> {

    /**
     * The entity was created and persisted locally.
     *
     * @property value the created entity.
     * @property undo a token to delete the created entity via the standard delete path (DQC-1.7).
     */
    data class Success<out T>(
        val value: T,
        val undo: UndoToken
    ) : CreationResult<T>

    /**
     * Client-side validation failed; nothing was persisted. Save stays disabled while
     * any error is present. Keyed by field name so the UI can render inline errors.
     *
     * @property fieldErrors map of field name to human-readable error message; never empty.
     */
    data class ValidationError(
        val fieldErrors: Map<String, String>
    ) : CreationResult<Nothing> {
        init {
            require(fieldErrors.isNotEmpty()) { "ValidationError must carry at least one field error" }
        }
    }

    /**
     * The creation was rejected by the Backend on sync (e.g. a name collision from
     * another device). The user's original input is retained for correction and the
     * specific reason surfaced non-blockingly — never a silent discard (DQC-2.11).
     *
     * @property reason the specific, user-facing rejection reason.
     * @property retainedInput the original input, retained so the user can correct and retry.
     */
    data class Rejected(
        val reason: String,
        val retainedInput: Any?
    ) : CreationResult<Nothing> {
        init {
            require(reason.isNotBlank()) { "Rejected reason must not be blank" }
        }
    }

    /**
     * An unexpected failure prevented creation (e.g. an Inline_Goal_Capture transaction
     * rolled back). No partial state is persisted.
     *
     * @property cause the underlying throwable.
     */
    data class Failure(
        val cause: Throwable
    ) : CreationResult<Nothing>
}
