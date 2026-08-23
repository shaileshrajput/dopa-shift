package com.dopashift.data.repository;

import com.dopashift.data.local.dao.GoalChecklistDao;
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
public final class GoalChecklistItemRepositoryImpl_Factory implements Factory<GoalChecklistItemRepositoryImpl> {
  private final Provider<GoalChecklistDao> daoProvider;

  public GoalChecklistItemRepositoryImpl_Factory(Provider<GoalChecklistDao> daoProvider) {
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
