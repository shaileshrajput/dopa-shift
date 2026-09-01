# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager.ViewWithFragmentComponentBuilderEntryPoint { *; }

# Keep Moshi adapters
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}

# Tink (via androidx.security-crypto) references compile-only errorprone
# annotations that are absent at runtime. Suppress the R8 missing-class warnings.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

-dontwarn com.google.errorprone.annotations.InlineMe

# Keep Tink crypto classes (used for encrypted storage of LLM API keys).
-keep class com.google.crypto.tink.** { *; }

# Tink's optional KeysDownloader references remote-key-fetching dependencies
# (Google API HTTP client, Joda-Time) that we don't use. Suppress the warnings.
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**
