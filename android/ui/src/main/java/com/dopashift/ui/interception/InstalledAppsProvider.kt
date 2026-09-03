package com.dopashift.ui.interception

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A single installed, launchable app candidate presented on the Rule_Authoring_Screen.
 *
 * The icon is exposed as a lazy accessor rather than eagerly loaded so the pure
 * sorting/filtering/exclusion logic can be exercised without a real PackageManager
 * or Drawable resources.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val iconLoader: () -> Drawable? = { null },
) {
    /** Loads the app icon on demand. Returns null when unavailable (e.g. in tests). */
    fun loadIcon(): Drawable? = iconLoader()
}

/**
 * Enumerates installed, launchable apps for the Rule_Authoring_Screen.
 *
 * Responsibilities:
 * - Query the [PackageManager] for launchable activities (ACTION_MAIN / CATEGORY_LAUNCHER).
 * - Resolve the set of system-critical packages to exclude (this app, the launcher/home,
 *   the dialer, and system settings) — Requirement 2.8.
 * - Return the candidate apps sorted case-insensitively alphabetically by label — Requirement 2.1.
 *
 * Testability: the enumeration query ([queryLaunchableApps]) and the exclusion resolution
 * ([resolveExcludedPackages]) are thin wrappers over the [PackageManager]. The actual
 * sorting/filtering/exclusion is performed by the pure [filterAndSort] function, which
 * takes a raw candidate list plus the excluded-package set and returns the final result.
 * This lets unit/property tests drive the logic without a real [PackageManager].
 */
@Singleton
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Returns installed launchable apps, excluding system-critical apps, sorted
     * case-insensitively alphabetically by label.
     */
    fun getInstalledApps(): List<InstalledApp> {
        val candidates = queryLaunchableApps()
        val excluded = resolveExcludedPackages()
        return filterAndSort(candidates, excluded)
    }

    /**
     * Pure logic: exclude any candidate whose package is in [excludedPackages], then
     * sort the remainder case-insensitively alphabetically by label. Deduplicates by
     * package name (keeping the first occurrence) since a package may expose multiple
     * launcher activities.
     *
     * Separated from all PackageManager access so it can be property-tested directly.
     */
    fun filterAndSort(
        candidates: List<InstalledApp>,
        excludedPackages: Set<String>,
    ): List<InstalledApp> {
        return candidates
            .asSequence()
            .filter { it.packageName !in excludedPackages }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    /**
     * Thin PackageManager query: resolves all launchable activities via an
     * ACTION_MAIN / CATEGORY_LAUNCHER intent and maps them to [InstalledApp] candidates.
     */
    private fun queryLaunchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, 0).map { resolveInfo ->
            val packageName = resolveInfo.activityInfo.packageName
            InstalledApp(
                packageName = packageName,
                label = resolveInfo.loadLabel(pm).toString(),
                iconLoader = { runCatching { resolveInfo.loadIcon(pm) }.getOrNull() },
            )
        }
    }

    /**
     * Resolves the set of system-critical packages to exclude (Requirement 2.8):
     * this app, the current launcher/home app, the default dialer, and system settings.
     * Unresolved entries are simply omitted so exclusion is best-effort and robust.
     */
    private fun resolveExcludedPackages(): Set<String> {
        val pm = context.packageManager
        return buildSet {
            // This app itself.
            add(context.packageName)
            // Current launcher / home app.
            resolvePackage(pm, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                ?.let(::add)
            // Default dialer / phone app.
            resolvePackage(pm, Intent(Intent.ACTION_DIAL))?.let(::add)
            // System settings app.
            resolvePackage(pm, Intent(Settings.ACTION_SETTINGS))?.let(::add)
        }
    }

    /**
     * Resolves the package name of the activity that would handle [intent], or null
     * if nothing resolves or only the Android system resolver activity is returned.
     */
    private fun resolvePackage(pm: PackageManager, intent: Intent): String? {
        val resolved: ResolveInfo? = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val packageName = resolved?.activityInfo?.packageName ?: return null
        // "android" is the fallback resolver activity, not a concrete app to exclude.
        return packageName.takeUnless { it == "android" }
    }
}
