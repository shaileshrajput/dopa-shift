package com.dopashift.ui.tasks;

import com.dopashift.domain.repository.DailyTodoRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class DailyTasksViewModel_Factory implements Factory<DailyTasksViewModel> {
  private final Provider<DailyTodoRepository> dailyTodoRepositoryProvider;

  public DailyTasksViewModel_Factory(Provider<DailyTodoRepository> dailyTodoRepositoryProvider) {
    this.dailyTodoRepositoryProvider = dailyTodoRepositoryProvider;
  }

  @Override
  public DailyTasksViewModel get() {
    return newInstance(dailyTodoRepositoryProvider.get());
  }

  public static DailyTasksViewModel_Factory create(
      Provider<DailyTodoRepository> dailyTodoRepositoryProvider) {
    return new DailyTasksViewModel_Factory(dailyTodoRepositoryProvider);
  }

  public static DailyTasksViewModel newInstance(DailyTodoRepository dailyTodoRepository) {
    return new DailyTasksViewModel(dailyTodoRepository);
  }
}
