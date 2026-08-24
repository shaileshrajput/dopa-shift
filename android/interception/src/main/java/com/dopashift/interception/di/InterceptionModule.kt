package com.dopashift.interception.di

import android.content.Context
import com.dopashift.interception.AllowanceTracker
import com.dopashift.interception.InterceptionConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
}
