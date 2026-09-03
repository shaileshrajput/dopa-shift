package com.dopashift.data.asset

import android.content.Context
import com.dopashift.domain.creation.CategoryKeywordProvider
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asset-backed implementation of [CategoryKeywordProvider] (DQC-2.2, DQC-2.3).
 *
 * Reads the bundled `assets/category_keywords.json` via the Android [android.content.res.AssetManager]
 * so preset categories and their suggested keywords are available fully offline with no
 * endpoint in v1 (Assumption 3). The mapping is parsed once and cached.
 *
 * Category lookup is case-insensitive so a preset picked from the UI resolves regardless
 * of exact casing; a free-text category with no bundled mapping yields an empty keyword list.
 */
@Singleton
class AssetCategoryKeywordProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) : CategoryKeywordProvider {

    private companion object {
        const val ASSET_PATH = "category_keywords.json"
    }

    /** Preset categories in bundled order; lazily loaded and cached. */
    private val entries: List<CategoryKeywordEntry> by lazy { loadEntries() }

    /** Case-insensitive lookup from category name to its bundled keywords. */
    private val keywordsByCategory: Map<String, List<String>> by lazy {
        entries.associate { it.name.lowercase() to it.keywords }
    }

    override fun presetCategories(): List<String> = entries.map { it.name }

    override fun suggestedKeywords(category: String): List<String> =
        keywordsByCategory[category.trim().lowercase()] ?: emptyList()

    private fun loadEntries(): List<CategoryKeywordEntry> {
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val adapter = moshi.adapter(CategoryKeywordsAsset::class.java)
        val parsed = adapter.fromJson(json)
            ?: error("Failed to parse bundled asset: $ASSET_PATH")
        return parsed.categories
    }
}
