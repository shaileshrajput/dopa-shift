package com.dopashift.data.repository;

import com.dopashift.data.local.dao.DailyTodoDao;
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
public final class DailyTodoRepositoryImpl_Factory implements Factory<DailyTodoRepositoryImpl> {
  private final Provider<DailyTodoDao> dailyTodoDaoProvider;

  public DailyTodoRepositoryImpl_Factory(Provider<DailyTodoDao> dailyTodoDaoProvider) {
    this.dailyTodoDaoProvider = dailyTodoDaoProvider;
  }

  @Override
  public DailyTodoRepositoryImpl get() {
    return newInstance(dailyTodoDaoProvider.get());
  }

  public static DailyTodoRepositoryImpl_Factory create(
      Provider<DailyTodoDao> dailyTodoDaoProvider) {
    return new DailyTodoRepositoryImpl_Factory(dailyTodoDaoProvider);
  }

  public static DailyTodoRepositoryImpl newInstance(DailyTodoDao dailyTodoDao) {
    return new DailyTodoRepositoryImpl(dailyTodoDao);
  }
}
