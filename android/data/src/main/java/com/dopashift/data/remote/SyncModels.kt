@file:Suppress("unused")

package com.dopashift.data.remote

/**
 * Type aliases preserving backward compatibility for the sync module.
 * The canonical DTO definitions are in com.dopashift.data.remote.dto.
 */
typealias SyncEvent = com.dopashift.data.remote.dto.SyncEvent
typealias SyncPushRequest = com.dopashift.data.remote.dto.SyncPushRequest
typealias SyncPushResponse = com.dopashift.data.remote.dto.SyncPushResponse
typealias SyncPullResponse = com.dopashift.data.remote.dto.SyncPullResponse
typealias ServerTimestamp = com.dopashift.data.remote.dto.ServerTimestamp
typealias ResolvedConflict = com.dopashift.data.remote.dto.ResolvedConflict
