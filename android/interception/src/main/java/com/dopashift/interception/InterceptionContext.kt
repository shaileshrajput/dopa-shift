package com.dopashift.interception

/**
 * Abstraction of device/permission context for testability.
 * Production code uses [AndroidInterceptionContext]; tests use a fake implementation.
 */
interface InterceptionContext {
    val hasUsageStats: Boolean
    val hasOverlay: Boolean
    val isLowRamDevice: Boolean
}
