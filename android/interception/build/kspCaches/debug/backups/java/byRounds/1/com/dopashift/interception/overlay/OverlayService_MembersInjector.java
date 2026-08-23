package com.dopashift.interception.overlay;

import com.dopashift.domain.repository.DailyTodoRepository;
import com.dopashift.domain.repository.HabitTrackRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class OverlayService_MembersInjector implements MembersInjector<OverlayService> {
  private final Provider<DailyTodoRepository> dailyTodoRepositoryProvider;

  private final Provider<HabitTrackRepository> habitTrackRepositoryProvider;

  public OverlayService_MembersInjector(Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider) {
    this.dailyTodoRepositoryProvider = dailyTodoRepositoryProvider;
    this.habitTrackRepositoryProvider = habitTrackRepositoryProvider;
  }

  public static MembersInjector<OverlayService> create(
      Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider) {
    return new OverlayService_MembersInjector(dailyTodoRepositoryProvider, habitTrackRepositoryProvider);
  }

  @Override
  public void injectMembers(OverlayService instance) {
    injectDailyTodoRepository(instance, dailyTodoRepositoryProvider.get());
    injectHabitTrackRepository(instance, habitTrackRepositoryProvider.get());
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
}
