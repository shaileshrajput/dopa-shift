package com.dopashift.domain.creation

import java.util.UUID

/**
 * A lightweight reference to a persisted entity, identifying it by its logical
 * [type] and stable [id] so it can be deleted through the standard delete path
 * when an Undo is invoked (DQC-1.7).
 *
 * @property type the entity's logical type, e.g. "goal", "habit_track", "todo".
 * @property id the entity's stable identifier.
 */
data class EntityRef(
    val type: String,
    val id: UUID
) {
    init {
        require(type.isNotBlank()) { "EntityRef type must not be blank" }
    }
}
