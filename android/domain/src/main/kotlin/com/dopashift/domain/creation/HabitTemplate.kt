package com.dopashift.domain.creation

/**
 * A bundled 30-day habit template for a goal category (DQC-3.5, DQC-3.6).
 *
 * Templates ship as APK assets so template-mode habit authoring works fully offline
 * (Assumption 3). App-authored template text is localizable (en/hi/mr), distinct from
 * user-generated content which is not translated.
 *
 * A template always carries exactly 30 checkpoint descriptions, one per day, each
 * 1-200 characters — matching the [com.dopashift.domain.entity.HabitCheckpoint]
 * invariant — so it resolves directly to a valid [com.dopashift.domain.entity.HabitTrack].
 *
 * @property id the stable template identifier (referenced by [HabitAuthoring.Template]).
 * @property category the goal category this template is appropriate for.
 * @property title a short, user-facing template title.
 * @property checkpointDescriptions the 30 ordered day-by-day checkpoint descriptions.
 */
data class HabitTemplate(
    val id: String,
    val category: String,
    val title: String,
    val checkpointDescriptions: List<String>
) {
    init {
        require(id.isNotBlank()) { "HabitTemplate id must not be blank" }
        require(category.isNotBlank()) { "HabitTemplate category must not be blank" }
        require(title.isNotBlank()) { "HabitTemplate title must not be blank" }
        require(checkpointDescriptions.size == CHECKPOINT_COUNT) {
            "HabitTemplate must have exactly $CHECKPOINT_COUNT checkpoints, was ${checkpointDescriptions.size}"
        }
        require(checkpointDescriptions.all { it.length in 1..DESCRIPTION_MAX }) {
            "Each checkpoint description must be 1-$DESCRIPTION_MAX characters"
        }
    }

    companion object {
        /** Every habit template resolves to exactly this many daily checkpoints. */
        const val CHECKPOINT_COUNT = 30

        /** Maximum length of a single checkpoint description, mirroring HabitCheckpoint. */
        const val DESCRIPTION_MAX = 200
    }
}
