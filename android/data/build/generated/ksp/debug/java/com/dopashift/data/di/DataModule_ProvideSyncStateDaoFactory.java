package com.dopashift.data.di;

import com.dopashift.data.local.DopaShiftDatabase;
import com.dopashift.data.local.dao.SyncStateDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
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
public final class DataModule_ProvideSyncStateDaoFactory implements Factory<SyncStateDao> {
  private final Provider<DopaShiftDatabase> dbProvider;

  public DataModule_ProvideSyncStateDaoFactory(Provider<DopaShiftDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public SyncStateDao get() {
    return provideSyncStateDao(dbProvider.get());
  }

  public static DataModule_ProvideSyncStateDaoFactory create(
      Provider<DopaShiftDatabase> dbProvider) {
    return new DataModule_ProvideSyncStateDaoFactory(dbProvider);
  }

  public static SyncStateDao provideSyncStateDao(DopaShiftDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideSyncStateDao(db));
  }
}
