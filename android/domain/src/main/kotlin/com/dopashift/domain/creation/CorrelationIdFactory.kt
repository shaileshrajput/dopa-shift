package com.dopashift.domain.creation

/**
 * Generates a [CorrelationId] at the point of origin of a Dashboard creation action
 * (DQC-6.3).
 *
 * A creation action's Correlation_ID is minted once — when the Quick_Create_Menu or a
 * Creation_Sheet opens — and then propagated through every downstream use-case call,
 * local write, and diagnostic event for that action. Declaring the factory as a
 * pure-Kotlin `domain` port keeps id generation swappable (a deterministic factory in
 * tests, a random one in production) without the ViewModel reaching for
 * [CorrelationId.random] directly, which would make correlation untestable.
 *
 * The default implementation ([Random]) simply delegates to [CorrelationId.random]; the
 * `app`/`ui` layer provides it via DI and tests substitute a fixed-sequence factory.
 */
fun interface CorrelationIdFactory {

    /**
     * Mints a fresh [CorrelationId] for a newly originated creation action.
     *
     * @return a new Correlation_ID to propagate through the whole action.
     */
    fun newId(): CorrelationId

    companion object {
        /** A [CorrelationIdFactory] that mints a random [CorrelationId] per call. */
        val Random: CorrelationIdFactory = CorrelationIdFactory { CorrelationId.random() }
    }
}
