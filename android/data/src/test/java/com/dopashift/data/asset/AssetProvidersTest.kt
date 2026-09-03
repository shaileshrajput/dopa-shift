package com.dopashift.data.asset

import androidx.test.core.app.ApplicationProvider
import com.dopashift.domain.creation.HabitTemplate
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the bundled asset providers (DQC-2.2, DQC-2.3).
 *
 * Both [AssetCategoryKeywordProvider] and [AssetHabitTemplateProvider] read real JSON
 * from the module's `src/main/assets` via the Android AssetManager, so they need a real
 * [android.content.Context]. Robolectric supplies one on the JVM (the module enables
 * `unitTests.isIncludeAndroidResources`), letting these run as fast JVM tests without an
 * emulator while still exercising the real bundled content.
 *
 * The Moshi instance mirrors the production wiring in `NetworkModule.provideMoshi()`
 * (KotlinJsonAdapterFactory) so parsing behavior matches the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AssetProvidersTest {

    private companion object {
        /** The six preset categories the goal sheet must offer at minimum (DQC-2.2). */
        val REQUIRED_PRESET_CATEGORIES = listOf(
            "Career Growth", "Fitness", "Build Business", "Clear Exam", "Learning", "Wellbeing"
        )
    }

    private lateinit var moshi: Moshi
    private lateinit var keywordProvider: AssetCategoryKeywordProvider
    private lateinit var templateProvider: AssetHabitTemplateProvider

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        keywordProvider = AssetCategoryKeywordProvider(context, moshi)
        templateProvider = AssetHabitTemplateProvider(context, moshi)
    }

    // === AssetCategoryKeywordProvider: preset categories (DQC-2.2) ===

    @Test
    fun `presetCategories includes all six required categories`() {
        val presets = keywordProvider.presetCategories()

        // DQC-2.2: at minimum these six categories are offered.
        assertTrue(
            "presets $presets must contain all required categories $REQUIRED_PRESET_CATEGORIES",
            presets.containsAll(REQUIRED_PRESET_CATEGORIES)
        )
    }

    @Test
    fun `presetCategories preserves bundled order`() {
        // The bundled JSON lists the categories in this exact order; presetCategories
        // must preserve it so the picker is deterministic.
        assertEquals(REQUIRED_PRESET_CATEGORIES, keywordProvider.presetCategories())
    }

    // === AssetCategoryKeywordProvider: suggested keywords (DQC-2.3) ===

    @Test
    fun `suggestedKeywords returns eight non-blank keywords for every preset category`() {
        REQUIRED_PRESET_CATEGORIES.forEach { category ->
            val keywords = keywordProvider.suggestedKeywords(category)

            assertEquals(
                "category '$category' must have 8 bundled keywords",
                8, keywords.size
            )
            assertTrue(
                "category '$category' keywords must all be non-blank: $keywords",
                keywords.all { it.isNotBlank() }
            )
            // DQC-2.1: each keyword must satisfy the 1-50 char goal-keyword bound.
            assertTrue(
                "category '$category' keywords must be 1-50 chars: $keywords",
                keywords.all { it.length in 1..50 }
            )
        }
    }

    @Test
    fun `suggestedKeywords lookup is case-insensitive`() {
        val canonical = keywordProvider.suggestedKeywords("Career Growth")
        assertEquals(canonical, keywordProvider.suggestedKeywords("career growth"))
        assertEquals(canonical, keywordProvider.suggestedKeywords("CAREER GROWTH"))
        // Surrounding whitespace is trimmed before lookup.
        assertEquals(canonical, keywordProvider.suggestedKeywords("  career growth  "))
    }

    @Test
    fun `suggestedKeywords returns empty list for unknown free-text category`() {
        // A free-text category with no bundled mapping yields no suggestions (DQC-2.3).
        assertTrue(keywordProvider.suggestedKeywords("Underwater Basket Weaving").isEmpty())
        assertTrue(keywordProvider.suggestedKeywords("").isEmpty())
    }

    // === AssetHabitTemplateProvider: checkpoint counts (DQC-3.6 support) ===

    @Test
    fun `every preset category has at least one template with exactly 30 checkpoints`() {
        REQUIRED_PRESET_CATEGORIES.forEach { category ->
            val templates = templateProvider.templatesForCategory(category)

            assertTrue(
                "category '$category' must have at least one bundled template",
                templates.isNotEmpty()
            )
            templates.forEach { template ->
                assertEquals(
                    "template '${template.id}' for '$category' must have exactly 30 checkpoints",
                    HabitTemplate.CHECKPOINT_COUNT,
                    template.checkpointDescriptions.size
                )
                assertTrue(
                    "template '${template.id}' checkpoints must be 1-200 chars",
                    template.checkpointDescriptions.all { it.length in 1..HabitTemplate.DESCRIPTION_MAX }
                )
            }
        }
    }

    @Test
    fun `defaultTemplate for a preset category returns a 30-checkpoint template of that category`() {
        REQUIRED_PRESET_CATEGORIES.forEach { category ->
            val template = templateProvider.defaultTemplate(category)

            assertEquals(
                "defaultTemplate for '$category' must belong to that category",
                category, template.category
            )
            assertEquals(
                "defaultTemplate for '$category' must have exactly 30 checkpoints",
                HabitTemplate.CHECKPOINT_COUNT,
                template.checkpointDescriptions.size
            )
        }
    }

    @Test
    fun `defaultTemplate falls back to a bundled template for an unknown category`() {
        // No template matches this category, so defaultTemplate falls back to any bundled
        // template rather than throwing (still a valid 30-checkpoint template).
        val template = templateProvider.defaultTemplate("Unknown Category")
        assertEquals(
            HabitTemplate.CHECKPOINT_COUNT,
            template.checkpointDescriptions.size
        )
    }

    @Test
    fun `templatesForCategory lookup is case-insensitive`() {
        val canonical = templateProvider.templatesForCategory("Fitness").map { it.id }.toSet()
        val lowered = templateProvider.templatesForCategory("fitness").map { it.id }.toSet()
        assertEquals(canonical, lowered)
    }
}
