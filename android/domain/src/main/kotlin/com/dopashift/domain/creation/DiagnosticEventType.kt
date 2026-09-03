package com.dopashift.domain.creation

/**
 * Machine-parseable event-type field for the structured local diagnostic events
 * recorded for Dashboard creation flows (DQC-6.4).
 *
 * Raw records carrying these types are never synced; only aggregated per-type
 * counts leave the device (DQC-6.5).
 */
enum class DiagnosticEventType {
    /** The Quick_Create_Menu or a Creation_Sheet was opened. */
    QUICK_CREATE_OPENED,

    /** An entity (goal, habit, or to-do) was successfully created. */
    ENTITY_CREATED,

    /** A creation flow was dismissed without persisting an entity. */
    CREATION_ABANDONED,

    /** A just-created entity was removed via the Undo action. */
    UNDO_INVOKED
}
