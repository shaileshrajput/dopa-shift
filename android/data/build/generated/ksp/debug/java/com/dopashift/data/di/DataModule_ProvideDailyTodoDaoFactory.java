package com.dopashift.data.di;

import com.dopashift.data.local.DopaShiftDatabase;
import com.dopashift.data.local.dao.DailyTodoDao;
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
public final class DataModule_ProvideDailyTodoDaoFactory implements Factory<DailyTodoDao> {
  private final Provider<DopaShiftDatabase> dbProvider;

  public DataModule_ProvideDailyTodoDaoFactory(Provider<DopaShiftDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DailyTodoDao get() {
    return provideDailyTodoDao(dbProvider.get());
  }

  public static DataModule_ProvideDailyTodoDaoFactory create(
      Provider<DopaShiftDatabase> dbProvider) {
    return new DataModule_ProvideDailyTodoDaoFactory(dbProvider);
  }

  public static DailyTodoDao provideDailyTodoDao(DopaShiftDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideDailyTodoDao(db));
  }
}
