package com.dopashift.sync.di;

import com.dopashift.data.local.dao.ChangeLogDao;
import com.dopashift.data.local.dao.SyncStateDao;
import com.dopashift.data.remote.DopaShiftApi;
import com.dopashift.sync.SyncManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class SyncModule_ProvideSyncManagerFactory implements Factory<SyncManager> {
  private final Provider<ChangeLogDao> changeLogDaoProvider;

  private final Provider<SyncStateDao> syncStateDaoProvider;

  private final Provider<DopaShiftApi> apiProvider;

  public SyncModule_ProvideSyncManagerFactory(Provider<ChangeLogDao> changeLogDaoProvider,
      Provider<SyncStateDao> syncStateDaoProvider, Provider<DopaShiftApi> apiProvider) {
    this.changeLogDaoProvider = changeLogDaoProvider;
    this.syncStateDaoProvider = syncStateDaoProvider;
    this.apiProvider = apiProvider;
  }

  @Override
  public SyncManager get() {
    return provideSyncManager(changeLogDaoProvider.get(), syncStateDaoProvider.get(), apiProvider.get());
  }

  public static SyncModule_ProvideSyncManagerFactory create(
      Provider<ChangeLogDao> changeLogDaoProvider, Provider<SyncStateDao> syncStateDaoProvider,
      Provider<DopaShiftApi> apiProvider) {
    return new SyncModule_ProvideSyncManagerFactory(changeLogDaoProvider, syncStateDaoProvider, apiProvider);
  }

  public static SyncManager provideSyncManager(ChangeLogDao changeLogDao, SyncStateDao syncStateDao,
      DopaShiftApi api) {
    return Preconditions.checkNotNullFromProvides(SyncModule.INSTANCE.provideSyncManager(changeLogDao, syncStateDao, api));
  }
}
