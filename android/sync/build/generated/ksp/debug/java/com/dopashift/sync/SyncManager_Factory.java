package com.dopashift.sync;

import com.dopashift.data.local.dao.ChangeLogDao;
import com.dopashift.data.local.dao.SyncStateDao;
import com.dopashift.data.remote.DopaShiftApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class SyncManager_Factory implements Factory<SyncManager> {
  private final Provider<ChangeLogDao> changeLogDaoProvider;

  private final Provider<SyncStateDao> syncStateDaoProvider;

  private final Provider<DopaShiftApi> apiProvider;

  public SyncManager_Factory(Provider<ChangeLogDao> changeLogDaoProvider,
      Provider<SyncStateDao> syncStateDaoProvider, Provider<DopaShiftApi> apiProvider) {
    this.changeLogDaoProvider = changeLogDaoProvider;
    this.syncStateDaoProvider = syncStateDaoProvider;
    this.apiProvider = apiProvider;
  }

  @Override
  public SyncManager get() {
    return newInstance(changeLogDaoProvider.get(), syncStateDaoProvider.get(), apiProvider.get());
  }

  public static SyncManager_Factory create(Provider<ChangeLogDao> changeLogDaoProvider,
      Provider<SyncStateDao> syncStateDaoProvider, Provider<DopaShiftApi> apiProvider) {
    return new SyncManager_Factory(changeLogDaoProvider, syncStateDaoProvider, apiProvider);
  }

  public static SyncManager newInstance(ChangeLogDao changeLogDao, SyncStateDao syncStateDao,
      DopaShiftApi api) {
    return new SyncManager(changeLogDao, syncStateDao, api);
  }
}
