package com.dopashift.data.di;

import com.dopashift.data.local.DopaShiftDatabase;
import com.dopashift.data.local.dao.GoalDao;
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
public final class DataModule_ProvideGoalDaoFactory implements Factory<GoalDao> {
  private final Provider<DopaShiftDatabase> dbProvider;

  public DataModule_ProvideGoalDaoFactory(Provider<DopaShiftDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public GoalDao get() {
    return provideGoalDao(dbProvider.get());
  }

  public static DataModule_ProvideGoalDaoFactory create(Provider<DopaShiftDatabase> dbProvider) {
    return new DataModule_ProvideGoalDaoFactory(dbProvider);
  }

  public static GoalDao provideGoalDao(DopaShiftDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideGoalDao(db));
  }
}
