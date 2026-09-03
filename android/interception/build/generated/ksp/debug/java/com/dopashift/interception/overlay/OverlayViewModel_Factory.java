package com.dopashift.interception.overlay;

import com.dopashift.domain.repository.DailyTodoRepository;
import com.dopashift.domain.repository.GoalRepository;
import com.dopashift.domain.repository.HabitTrackRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class OverlayViewModel_Factory implements Factory<OverlayViewModel> {
  private final Provider<DailyTodoRepository> dailyTodoRepositoryProvider;

  private final Provider<HabitTrackRepository> habitTrackRepositoryProvider;

  private final Provider<GoalRepository> goalRepositoryProvider;

  private final Provider<OverlayTodoCompletionHandler> todoCompletionHandlerProvider;

  private final Provider<OverlayInlineGoalCaptureHandler> inlineGoalCaptureHandlerProvider;

  private OverlayViewModel_Factory(Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider,
      Provider<GoalRepository> goalRepositoryProvider,
      Provider<OverlayTodoCompletionHandler> todoCompletionHandlerProvider,
      Provider<OverlayInlineGoalCaptureHandler> inlineGoalCaptureHandlerProvider) {
    this.dailyTodoRepositoryProvider = dailyTodoRepositoryProvider;
    this.habitTrackRepositoryProvider = habitTrackRepositoryProvider;
    this.goalRepositoryProvider = goalRepositoryProvider;
    this.todoCompletionHandlerProvider = todoCompletionHandlerProvider;
    this.inlineGoalCaptureHandlerProvider = inlineGoalCaptureHandlerProvider;
  }

  @Override
  public OverlayViewModel get() {
    return newInstance(dailyTodoRepositoryProvider.get(), habitTrackRepositoryProvider.get(), goalRepositoryProvider.get(), todoCompletionHandlerProvider.get(), inlineGoalCaptureHandlerProvider.get());
  }

  public static OverlayViewModel_Factory create(
      Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider,
      Provider<GoalRepository> goalRepositoryProvider,
      Provider<OverlayTodoCompletionHandler> todoCompletionHandlerProvider,
      Provider<OverlayInlineGoalCaptureHandler> inlineGoalCaptureHandlerProvider) {
    return new OverlayViewModel_Factory(dailyTodoRepositoryProvider, habitTrackRepositoryProvider, goalRepositoryProvider, todoCompletionHandlerProvider, inlineGoalCaptureHandlerProvider);
  }

  public static OverlayViewModel newInstance(DailyTodoRepository dailyTodoRepository,
      HabitTrackRepository habitTrackRepository, GoalRepository goalRepository,
      OverlayTodoCompletionHandler todoCompletionHandler,
      OverlayInlineGoalCaptureHandler inlineGoalCaptureHandler) {
    return new OverlayViewModel(dailyTodoRepository, habitTrackRepository, goalRepository, todoCompletionHandler, inlineGoalCaptureHandler);
  }
}
