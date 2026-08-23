package com.dopashift.sync.di

import com.dopashift.data.local.dao.ChangeLogDao
import com.dopashift.data.local.dao.SyncStateDao
import com.dopashift.data.remote.DopaShiftApi
import com.dopashift.sync.SyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideSyncManager(
        changeLogDao: ChangeLogDao,
        syncStateDao: SyncStateDao,
        api: DopaShiftApi
    ): SyncManager {
        return SyncManager(changeLogDao, syncStateDao, api)
    }
}
