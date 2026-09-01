package com.dopashift.interception.overlay;

import com.dopashift.domain.repository.ChangeLogRepository;
import com.dopashift.domain.repository.DailyTodoRepository;
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
public final class OverlayTodoCompletionHandler_Factory implements Factory<OverlayTodoCompletionHandler> {
  private final Provider<DailyTodoRepository> dailyTodoRepositoryProvider;

  private final Provider<ChangeLogRepository> changeLogRepositoryProvider;

  private OverlayTodoCompletionHandler_Factory(
      Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
      Provider<ChangeLogRepository> changeLogRepositoryProvider) {
    this.dailyTodoRepositoryProvider = dailyTodoRepositoryProvider;
    this.changeLogRepositoryProvider = changeLogRepositoryProvider;
  }

  @Override
  public OverlayTodoCompletionHandler get() {
    return newInstance(dailyTodoRepositoryProvider.get(), changeLogRepositoryProvider.get());
  }

  public static OverlayTodoCompletionHandler_Factory create(
      Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
      Provider<ChangeLogRepository> changeLogRepositoryProvider) {
    return new OverlayTodoCompletionHandler_Factory(dailyTodoRepositoryProvider, changeLogRepositoryProvider);
  }

  public static OverlayTodoCompletionHandler newInstance(DailyTodoRepository dailyTodoRepository,
      ChangeLogRepository changeLogRepository) {
    return new OverlayTodoCompletionHandler(dailyTodoRepository, changeLogRepository);
  }
}
