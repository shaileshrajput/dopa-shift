package com.dopashift.ui.goals;

import com.dopashift.domain.repository.GoalChecklistItemRepository;
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
public final class GoalsViewModel_Factory implements Factory<GoalsViewModel> {
  private final Provider<GoalRepository> goalRepositoryProvider;

  private final Provider<GoalChecklistItemRepository> checklistRepositoryProvider;

  private final Provider<HabitTrackRepository> habitTrackRepositoryProvider;

  public GoalsViewModel_Factory(Provider<GoalRepository> goalRepositoryProvider,
      Provider<GoalChecklistItemRepository> checklistRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider) {
    this.goalRepositoryProvider = goalRepositoryProvider;
    this.checklistRepositoryProvider = checklistRepositoryProvider;
    this.habitTrackRepositoryProvider = habitTrackRepositoryProvider;
  }

  @Override
  public GoalsViewModel get() {
    return newInstance(goalRepositoryProvider.get(), checklistRepositoryProvider.get(), habitTrackRepositoryProvider.get());
  }

  public static GoalsViewModel_Factory create(Provider<GoalRepository> goalRepositoryProvider,
      Provider<GoalChecklistItemRepository> checklistRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider) {
    return new GoalsViewModel_Factory(goalRepositoryProvider, checklistRepositoryProvider, habitTrackRepositoryProvider);
  }

  public static GoalsViewModel newInstance(GoalRepository goalRepository,
      GoalChecklistItemRepository checklistRepository, HabitTrackRepository habitTrackRepository) {
    return new GoalsViewModel(goalRepository, checklistRepository, habitTrackRepository);
  }
}
