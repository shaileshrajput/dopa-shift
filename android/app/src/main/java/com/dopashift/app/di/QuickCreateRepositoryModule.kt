package com.dopashift.app.di

import com.dopashift.app.creation.AndroidDeviceIdProvider
import com.dopashift.app.creation.SystemDomainClock
import com.dopashift.data.asset.AssetCategoryKeywordProvider
import com.dopashift.data.asset.AssetHabitTemplateProvider
import com.dopashift.data.repository.DataStoreDiagnosticsSink
import com.dopashift.data.repository.RoomDashboardSummaryRepository
import com.dopashift.data.repository.RoomTransactionRunner
import com.dopashift.domain.creation.CategoryKeywordProvider
import com.dopashift.domain.creation.DashboardSummaryRepository
import com.dopashift.domain.creation.DeviceIdProvider
import com.dopashift.domain.creation.DiagnosticsEventSink
import com.dopashift.domain.creation.DomainClock
import com.dopashift.domain.creation.HabitTemplateProvider
import com.dopashift.domain.creation.TransactionRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the Dashboard Quick-Create repository/service ports (task 13.1) to their concrete
 * `data`-module implementations. This is the Hexagonal Architecture boundary for the feature:
 * `domain` declares the ports, `data` implements them, and the `app` module wires them together.
 *
 * All bound implementations expose `@Inject` constructors whose dependencies (Room DAOs, the
 * [com.dopashift.data.local.DopaShiftDatabase], `Context`, and `Moshi`) are already provided by
 * the `data` module's `DataModule`/`NetworkModule`, so no extra `@Provides` are needed here.
 *
 * - [DashboardSummaryRepository] → [RoomDashboardSummaryRepository] (DQC-5.3)
 * - [TransactionRunner] → [RoomTransactionRunner] (atomic Inline_Goal_Capture, DQC-3.3)
 * - [HabitTemplateProvider] → [AssetHabitTemplateProvider] (bundled 30-day templates, DQC-3.5/3.6)
 * - [CategoryKeywordProvider] → [AssetCategoryKeywordProvider] (bundled keyword maps, DQC-2.3)
 * - [DiagnosticsEventSink] → [DataStoreDiagnosticsSink] (local-only diagnostics, DQC-6.4)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class QuickCreateRepositoryModule {

    @Binds
    abstract fun bindDashboardSummaryRepository(
        impl: RoomDashboardSummaryRepository
    ): DashboardSummaryRepository

    @Binds
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner

    @Binds
    abstract fun bindHabitTemplateProvider(
        impl: AssetHabitTemplateProvider
    ): HabitTemplateProvider

    @Binds
    abstract fun bindCategoryKeywordProvider(
        impl: AssetCategoryKeywordProvider
    ): CategoryKeywordProvider

    @Binds
    abstract fun bindDiagnosticsEventSink(impl: DataStoreDiagnosticsSink): DiagnosticsEventSink

    /**
     * The creation interactors ([com.dopashift.domain.creation.usecase.CreateGoalInteractor] et al.)
     * stamp entities and Change_Log events with a [DomainClock] time and a [DeviceIdProvider] id.
     * Both ports are pure-Kotlin in `domain` and implemented in the `app` layer so the interactors
     * stay framework-free and deterministic under a fixed test clock (DQC-6.1/6.2).
     */
    @Binds
    abstract fun bindDomainClock(impl: SystemDomainClock): DomainClock

    @Binds
    abstract fun bindDeviceIdProvider(impl: AndroidDeviceIdProvider): DeviceIdProvider
}
