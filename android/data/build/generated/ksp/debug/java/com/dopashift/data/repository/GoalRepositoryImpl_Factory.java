package com.dopashift.data.repository;

import com.dopashift.data.local.dao.GoalDao;
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
public final class GoalRepositoryImpl_Factory implements Factory<GoalRepositoryImpl> {
  private final Provider<GoalDao> goalDaoProvider;

  private GoalRepositoryImpl_Factory(Provider<GoalDao> goalDaoProvider) {
    this.goalDaoProvider = goalDaoProvider;
  }

  @Override
  public GoalRepositoryImpl get() {
    return newInstance(goalDaoProvider.get());
  }

  public static GoalRepositoryImpl_Factory create(Provider<GoalDao> goalDaoProvider) {
    return new GoalRepositoryImpl_Factory(goalDaoProvider);
  }

  public static GoalRepositoryImpl newInstance(GoalDao goalDao) {
    return new GoalRepositoryImpl(goalDao);
  }
}
