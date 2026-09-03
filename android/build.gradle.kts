// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Declared at the root (apply false) so the com.android.test plugin used by
    // the :macrobenchmark module joins the build classpath with a resolved
    // version, consistent with the other AGP plugins. Without this, applying
    // com.android.test in a submodule fails with "already on the classpath with
    // an unknown version" because AGP is on the classpath but unversioned there.
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
