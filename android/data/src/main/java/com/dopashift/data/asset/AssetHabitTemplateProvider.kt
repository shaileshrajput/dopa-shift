package com.dopashift.data.asset

import android.content.Context
import com.dopashift.domain.creation.HabitTemplate
import com.dopashift.domain.creation.HabitTemplateProvider
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asset-backed implementation of [HabitTemplateProvider] (DQC-3.5, DQC-3.6).
 *
 * Reads the bundled habit-template JSON files under `assets/habit_templates/` via the Android
 * [android.content.res.AssetManager] so template-mode habit authoring works fully offline
 * with no endpoint in v1 (Assumption 3). Each asset carries app-authored, localized
 * (en/hi/mr, OQ-2) title and checkpoint text; the current device locale is resolved with
 * an English fallback.
 *
 * Every emitted [HabitTemplate] carries exactly [HabitTemplate.CHECKPOINT_COUNT] checkpoints
 * (validated by the domain type's `init` block), so it resolves directly to a valid habit.
 */
@Singleton
class AssetHabitTemplateProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) : HabitTemplateProvider {

    private companion object {
        const val TEMPLATES_DIR = "habit_templates"
        const val DEFAULT_LOCALE = "en"
        val SUPPORTED_LOCALES = setOf("en", "hi", "mr")
    }

    /** All bundled template assets, parsed once and cached. */
    private val assets: List<HabitTemplateAsset> by lazy { loadAssets() }

    override fun templatesForCategory(category: String): List<HabitTemplate> {
        val target = category.trim().lowercase()
        return assets
            .filter { it.category.trim().lowercase() == target }
            .map { it.toDomain(resolveLocale()) }
    }

    override fun defaultTemplate(category: String): HabitTemplate {
        val locale = resolveLocale()
        return templatesForCategory(category).firstOrNull()
            ?: assets.firstOrNull()?.toDomain(locale)
            ?: error("No bundled habit templates are available")
    }

    private fun loadAssets(): List<HabitTemplateAsset> {
        val adapter = moshi.adapter(HabitTemplateAsset::class.java)
        val fileNames = context.assets.list(TEMPLATES_DIR)?.filter { it.endsWith(".json") }
            ?: emptyList()
        return fileNames.map { fileName ->
            val json = context.assets.open("$TEMPLATES_DIR/$fileName")
                .bufferedReader()
                .use { it.readText() }
            adapter.fromJson(json)
                ?: error("Failed to parse bundled habit template asset: $fileName")
        }
    }

    /** Maps the device language to a supported locale, defaulting to English. */
    private fun resolveLocale(): String {
        val lang = Locale.getDefault().language.lowercase()
        return if (lang in SUPPORTED_LOCALES) lang else DEFAULT_LOCALE
    }

    private fun HabitTemplateAsset.toDomain(locale: String): HabitTemplate {
        val title = titles[locale] ?: titles[DEFAULT_LOCALE]
            ?: error("Habit template $id is missing a title for '$DEFAULT_LOCALE'")
        val descriptions = checkpoints[locale] ?: checkpoints[DEFAULT_LOCALE]
            ?: error("Habit template $id is missing checkpoints for '$DEFAULT_LOCALE'")
        return HabitTemplate(
            id = id,
            category = category,
            title = title,
            checkpointDescriptions = descriptions
        )
    }
}
