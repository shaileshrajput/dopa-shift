package com.dopashift.domain.creation

/**
 * Supplies the preset goal categories and their locally bundled suggested keywords
 * (DQC-2.2, DQC-2.3).
 *
 * The category-to-keyword mapping ships as an APK asset so goal creation works fully
 * offline (Assumption 3). Declared in `domain` as a pure-Kotlin port; the asset-reading
 * implementation lives in the `data` module (`AssetCategoryKeywordProvider`).
 */
interface CategoryKeywordProvider {

    /**
     * Returns the preset category names offered in the goal Creation_Sheet's picker.
     * At minimum: Career Growth, Fitness, Build Business, Clear Exam, Learning, Wellbeing (DQC-2.2).
     *
     * @return the ordered list of preset category names.
     */
    fun presetCategories(): List<String>

    /**
     * Returns the bundled suggested keywords for [category], pre-populated in the goal
     * sheet and individually editable/removable before saving (DQC-2.3). Returns an
     * empty list for a free-text category with no bundled mapping.
     *
     * @param category the selected category to suggest keywords for.
     * @return the suggested keywords defined for that category in the bundled mapping.
     */
    fun suggestedKeywords(category: String): List<String>
}
