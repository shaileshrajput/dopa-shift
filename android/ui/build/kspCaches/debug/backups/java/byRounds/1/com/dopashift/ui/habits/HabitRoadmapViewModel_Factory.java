package com.dopashift.ui.habits;

import com.dopashift.domain.repository.GoalRepository;
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
public final class HabitRoadmapViewModel_Factory implements Factory<HabitRoadmapViewModel> {
  private final Provider<HabitTrackRepository> habitTrackRepositoryProvider;

  private final Provider<GoalRepository> goalRepositoryProvider;

  public HabitRoadmapViewModel_Factory(Provider<HabitTrackRepository> habitTrackRepositoryProvider,
      Provider<GoalRepository> goalRepositoryProvider) {
    this.habitTrackRepositoryProvider = habitTrackRepositoryProvider;
    this.goalRepositoryProvider = goalRepositoryProvider;
  }

  @Override
  public HabitRoadmapViewModel get() {
    return newInstance(habitTrackRepositoryProvider.get(), goalRepositoryProvider.get());
  }

  public static HabitRoadmapViewModel_Factory create(
      Provider<HabitTrackRepository> habitTrackRepositoryProvider,
      Provider<GoalRepository> goalRepositoryProvider) {
    return new HabitRoadmapViewModel_Factory(habitTrackRepositoryProvider, goalRepositoryProvider);
  }

  public static HabitRoadmapViewModel newInstance(HabitTrackRepository habitTrackRepository,
      GoalRepository goalRepository) {
    return new HabitRoadmapViewModel(habitTrackRepository, goalRepository);
  }
}
