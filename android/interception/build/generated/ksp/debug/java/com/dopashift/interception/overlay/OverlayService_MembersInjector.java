package com.dopashift.interception.overlay;

import com.dopashift.domain.repository.DailyTodoRepository;
import com.dopashift.domain.repository.GoalRepository;
import com.dopashift.domain.repository.HabitTrackRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class OverlayService_MembersInjector implements MembersInjector<OverlayService> {
  private final Provider<DailyTodoRepository> dailyTodoRepositoryProvider;

  private final Provider<HabitTrackRepository> habitTrackRepositoryProvider;

  private final Provider<GoalRepository> goalRepositoryProvider;

  private final Provider<OverlayTodoCompletionHandler> todoCompletionHandlerProvider;

  private final Provider<OverlayInlineGoalCaptureHandler> inlineGoalCaptureHandlerProvider;

  private OverlayService_MembersInjector(Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
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
  public void injectMembers(OverlayService instance) {
    injectDailyTodoRepository(instance, dailyTodoRepositoryProvider.get());
    injectHabitTrackRepository(instance, habitTrackRepositoryProvider.get());
    injectGoalRepository(instance, goalRepositoryProvider.get());
    injectTodoCompletionHandler(instance, todoCompletionHandlerProvider.get());
    injectInlineGoalCaptureHandler(instance, inlineGoalCaptureHandlerProvider.get());
  }

  public static MembersInjector<OverlayService> create(
      Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider,
      Provider<GoalRepository> goalRepositoryProvider,
      Provider<OverlayTodoCompletionHandler> todoCompletionHandlerProvider,
      Provider<OverlayInlineGoalCaptureHandler> inlineGoalCaptureHandlerProvider) {
    return new OverlayService_MembersInjector(dailyTodoRepositoryProvider, habitTrackRepositoryProvider, goalRepositoryProvider, todoCompletionHandlerProvider, inlineGoalCaptureHandlerProvider);
  }

  @InjectedFieldSignature("com.dopashift.interception.overlay.OverlayService.dailyTodoRepository")
  public static void injectDailyTodoRepository(OverlayService instance,
      DailyTodoRepository dailyTodoRepository) {
    instance.dailyTodoRepository = dailyTodoRepository;
  }

  @InjectedFieldSignature("com.dopashift.interception.overlay.OverlayService.habitTrackRepository")
  public static void injectHabitTrackRepository(OverlayService instance,
      HabitTrackRepository habitTrackRepository) {
    instance.habitTrackRepository = habitTrackRepository;
  }

  @InjectedFieldSignature("com.dopashift.interception.overlay.OverlayService.goalRepository")
  public static void injectGoalRepository(OverlayService instance, GoalRepository goalRepository) {
    instance.goalRepository = goalRepository;
  }

  @InjectedFieldSignature("com.dopashift.interception.overlay.OverlayService.todoCompletionHandler")
  public static void injectTodoCompletionHandler(OverlayService instance,
      OverlayTodoCompletionHandler todoCompletionHandler) {
    instance.todoCompletionHandler = todoCompletionHandler;
  }

  @InjectedFieldSignature("com.dopashift.interception.overlay.OverlayService.inlineGoalCaptureHandler")
  public static void injectInlineGoalCaptureHandler(OverlayService instance,
      OverlayInlineGoalCaptureHandler inlineGoalCaptureHandler) {
    instance.inlineGoalCaptureHandler = inlineGoalCaptureHandler;
  }
}
