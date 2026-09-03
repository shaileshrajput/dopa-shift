package com.dopashift.interception.di

import android.content.Context
import com.dopashift.interception.AllowanceTracker
import com.dopashift.interception.AndroidSuppressionSignalSource
import com.dopashift.interception.InterceptionConfig
import com.dopashift.interception.SuppressionSignalSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Singleton

/**
 * Hilt module providing interception engine dependencies.
 *
 * Most interception components (AllowanceTracker, DeviceCapabilityChecker,
 * InterceptionPermissionHelper, InterceptionTodoReminderNotifier) are
 * constructor-injected singletons that Hilt resolves automatically.
 *
 * This module provides:
 * - Default [InterceptionConfig] for the polling service.
 *
 * Note: [java.time.Clock] is provided by [ReminderModule] and shared
 * across the interception and reminder subsystems.
 */
@Module
@InstallIn(SingletonComponent::class)
object InterceptionModule {

    /**
     * Provides the default interception polling configuration.
     * Can be overridden in tests by replacing this binding.
     */
    @Provides
    @Singleton
    fun provideInterceptionConfig(): InterceptionConfig {
        return InterceptionConfig()
    }

    /**
     * Provides the device-default [ZoneId] used by time-dependent interception components
     * (e.g. [com.dopashift.interception.FocusExtensionManager]) for local-midnight boundaries.
     * Components expose a setter to override this from the user's preferences; tests supply a
     * fixed zone directly via the component constructor.
     */
    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    /**
     * Provides the production [SuppressionSignalSource] backed by real Android system
     * services (telephony/audio) for [com.dopashift.interception.SuppressionContextDetector].
     * Tests replace this binding with a fake signal source.
     */
    @Provides
    @Singleton
    fun provideSuppressionSignalSource(
        @ApplicationContext context: Context
    ): SuppressionSignalSource {
        return AndroidSuppressionSignalSource(context)
    }
}
