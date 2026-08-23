package com.dopashift.data.di;

import com.dopashift.data.local.DopaShiftDatabase;
import com.dopashift.data.local.dao.GoalChecklistDao;
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
public final class DataModule_ProvideGoalChecklistDaoFactory implements Factory<GoalChecklistDao> {
  private final Provider<DopaShiftDatabase> dbProvider;

  public DataModule_ProvideGoalChecklistDaoFactory(Provider<DopaShiftDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public GoalChecklistDao get() {
    return provideGoalChecklistDao(dbProvider.get());
  }

  public static DataModule_ProvideGoalChecklistDaoFactory create(
      Provider<DopaShiftDatabase> dbProvider) {
    return new DataModule_ProvideGoalChecklistDaoFactory(dbProvider);
  }

  public static GoalChecklistDao provideGoalChecklistDao(DopaShiftDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideGoalChecklistDao(db));
  }
}
