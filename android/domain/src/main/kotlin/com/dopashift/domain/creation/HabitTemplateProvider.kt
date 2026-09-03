package com.dopashift.domain.creation

/**
 * Supplies bundled 30-day [HabitTemplate]s for template-mode habit authoring (DQC-3.5, DQC-3.6).
 *
 * Templates ship as APK assets, so this port works fully offline with no endpoint in
 * v1 (Assumption 3). Declared in `domain` as a pure-Kotlin port; the asset-reading
 * implementation lives in the `data` module (`AssetHabitTemplateProvider`).
 */
interface HabitTemplateProvider {

    /**
     * Returns the bundled templates appropriate for [category]. May be empty if no
     * category-specific template is bundled; callers can fall back to [defaultTemplate].
     *
     * @param category the goal category to look up templates for.
     * @return the matching templates, each carrying 30 checkpoints.
     */
    fun templatesForCategory(category: String): List<HabitTemplate>

    /**
     * Returns a single default template for [category], used when the user does not
     * pick a specific template (e.g. when LLM mode defaults back to Template).
     *
     * @param category the goal category to resolve a default template for.
     * @return a template carrying 30 checkpoints.
     */
    fun defaultTemplate(category: String): HabitTemplate
}
