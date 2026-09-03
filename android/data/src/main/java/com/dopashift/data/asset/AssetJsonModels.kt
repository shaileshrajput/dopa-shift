package com.dopashift.data.asset

import com.squareup.moshi.JsonClass

/**
 * Moshi models mirroring the bundled APK asset JSON for offline goal/habit creation
 * (Assumption 3, DQC-2.2, DQC-2.3, DQC-3.5, DQC-3.6).
 *
 * These are `data`-layer wire models only; they are mapped to pure-Kotlin domain types
 * ([com.dopashift.domain.creation.HabitTemplate]) before crossing the module boundary.
 */

/** Root of `assets/category_keywords.json`. */
@JsonClass(generateAdapter = true)
data class CategoryKeywordsAsset(
    val categories: List<CategoryKeywordEntry>
)

@JsonClass(generateAdapter = true)
data class CategoryKeywordEntry(
    val name: String,
    val keywords: List<String>
)

/**
 * Root of each habit-template JSON file under `assets/habit_templates/`.
 *
 * Template title and checkpoint text are app-authored and localized (en/hi/mr, OQ-2),
 * distinct from user-generated content which is never translated.
 */
@JsonClass(generateAdapter = true)
data class HabitTemplateAsset(
    val id: String,
    val category: String,
    // Locale code to localized title.
    val titles: Map<String, String>,
    // Locale code to ordered list of 30 checkpoint descriptions.
    val checkpoints: Map<String, List<String>>
)
