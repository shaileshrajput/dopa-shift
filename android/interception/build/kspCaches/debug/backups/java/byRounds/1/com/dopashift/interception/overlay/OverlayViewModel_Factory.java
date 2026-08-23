package com.dopashift.interception.overlay;

import com.dopashift.domain.repository.DailyTodoRepository;
import com.dopashift.domain.repository.HabitTrackRepository;
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
public final class OverlayViewModel_Factory implements Factory<OverlayViewModel> {
  private final Provider<DailyTodoRepository> dailyTodoRepositoryProvider;

  private final Provider<HabitTrackRepository> habitTrackRepositoryProvider;

  public OverlayViewModel_Factory(Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider) {
    this.dailyTodoRepositoryProvider = dailyTodoRepositoryProvider;
    this.habitTrackRepositoryProvider = habitTrackRepositoryProvider;
  }

  @Override
  public OverlayViewModel get() {
    return newInstance(dailyTodoRepositoryProvider.get(), habitTrackRepositoryProvider.get());
  }

  public static OverlayViewModel_Factory create(
      Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider) {
    return new OverlayViewModel_Factory(dailyTodoRepositoryProvider, habitTrackRepositoryProvider);
  }

  public static OverlayViewModel newInstance(DailyTodoRepository dailyTodoRepository,
      HabitTrackRepository habitTrackRepository) {
    return new OverlayViewModel(dailyTodoRepository, habitTrackRepository);
  }
}
