package com.dopashift.data.repository;

import com.dopashift.data.local.dao.GoalChecklistDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class GoalChecklistItemRepositoryImpl_Factory implements Factory<GoalChecklistItemRepositoryImpl> {
  private final Provider<GoalChecklistDao> daoProvider;

  private GoalChecklistItemRepositoryImpl_Factory(Provider<GoalChecklistDao> daoProvider) {
    this.daoProvider = daoProvider;
  }

  @Override
  public GoalChecklistItemRepositoryImpl get() {
    return newInstance(daoProvider.get());
  }

  public static GoalChecklistItemRepositoryImpl_Factory create(
      Provider<GoalChecklistDao> daoProvider) {
    return new GoalChecklistItemRepositoryImpl_Factory(daoProvider);
  }

  public static GoalChecklistItemRepositoryImpl newInstance(GoalChecklistDao dao) {
    return new GoalChecklistItemRepositoryImpl(dao);
  }
}
