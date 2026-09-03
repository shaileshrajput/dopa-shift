// Feature: dynamic-ui-experience, Task 19.1
package com.dopashift.macrobenchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector

/**
 * Shared constants and selectors for the DUX-6 Macrobenchmarks
 * (StartupBenchmark, ScrollBenchmark, PowerBenchmark).
 *
 * [TARGET_PACKAGE] is the app-under-test's applicationId, declared in
 * app/build.gradle.kts (`applicationId = "com.dopashift.app"`). Keep in sync if
 * the applicationId changes.
 */
internal const val TARGET_PACKAGE = "com.dopashift.app"

/** Timeout waiting for the Dashboard's first meaningful content to appear. */
internal const val CONTENT_TIMEOUT_MS = 5_000L

/**
 * Selector for the Dashboard's scrollable list. The Dashboard exposes a
 * scrollable container (LazyColumn/LazyVerticalGrid); UiAutomator locates it by
 * its scrollable property so the benchmark does not depend on a brittle
 * resource id. If the app adds a stable testTag/resource-id for the dashboard
 * list, prefer `By.res(...)` here.
 */
internal fun dashboardListSelector(): BySelector = By.scrollable(true)
