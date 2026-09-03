package com.dopashift.domain.creation

/**
 * Pure-Kotlin port that supplies the stable per-device identifier stamped onto every
 * [com.dopashift.domain.entity.ChangeLogEntry] this device appends (DQC-6.1, DQC-6.2).
 *
 * The `domain` module stays framework-free, so it cannot read Android's device/install
 * identifiers directly. This port is implemented in the `data`/`app` layer (the same
 * source the existing Change_Log writers use for `deviceId`) and injected into the
 * creation interactors, keeping them pure and testable with a fixed fake in tests.
 */
fun interface DeviceIdProvider {
    /**
     * @return the stable identifier for this device, matching the `deviceId` used by the
     *   existing Sync_Engine Change_Log pipeline so Dashboard-created events sync identically.
     */
    fun deviceId(): String
}
