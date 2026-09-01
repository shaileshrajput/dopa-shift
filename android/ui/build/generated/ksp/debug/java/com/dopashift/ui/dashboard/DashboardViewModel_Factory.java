package com.dopashift.ui.dashboard;

import com.dopashift.domain.repository.ChangeLogRepository;
import com.dopashift.domain.repository.DailyTodoRepository;
import com.dopashift.domain.repository.EfficiencyScoreRepository;
import com.dopashift.domain.repository.GoalChecklistItemRepository;
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
public final class DashboardViewModel_Factory implements Factory<DashboardViewModel> {
  private final Provider<GoalRepository> goalRepositoryProvider;

  private final Provider<DailyTodoRepository> dailyTodoRepositoryProvider;

  private final Provider<HabitTrackRepository> habitTrackRepositoryProvider;

  private final Provider<EfficiencyScoreRepository> efficiencyScoreRepositoryProvider;

  private final Provider<ChangeLogRepository> changeLogRepositoryProvider;

  private final Provider<GoalChecklistItemRepository> goalChecklistItemRepositoryProvider;

  private DashboardViewModel_Factory(Provider<GoalRepository> goalRepositoryProvider,
      Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider,
      Provider<EfficiencyScoreRepository> efficiencyScoreRepositoryProvider,
      Provider<ChangeLogRepository> changeLogRepositoryProvider,
      Provider<GoalChecklistItemRepository> goalChecklistItemRepositoryProvider) {
    this.goalRepositoryProvider = goalRepositoryProvider;
    this.dailyTodoRepositoryProvider = dailyTodoRepositoryProvider;
    this.habitTrackRepositoryProvider = habitTrackRepositoryProvider;
    this.efficiencyScoreRepositoryProvider = efficiencyScoreRepositoryProvider;
    this.changeLogRepositoryProvider = changeLogRepositoryProvider;
    this.goalChecklistItemRepositoryProvider = goalChecklistItemRepositoryProvider;
  }

  @Override
  public DashboardViewModel get() {
    return newInstance(goalRepositoryProvider.get(), dailyTodoRepositoryProvider.get(), habitTrackRepositoryProvider.get(), efficiencyScoreRepositoryProvider.get(), changeLogRepositoryProvider.get(), goalChecklistItemRepositoryProvider.get());
  }

  public static DashboardViewModel_Factory create(Provider<GoalRepository> goalRepositoryProvider,
      Provider<DailyTodoRepository> dailyTodoRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider,
      Provider<EfficiencyScoreRepository> efficiencyScoreRepositoryProvider,
      Provider<ChangeLogRepository> changeLogRepositoryProvider,
      Provider<GoalChecklistItemRepository> goalChecklistItemRepositoryProvider) {
    return new DashboardViewModel_Factory(goalRepositoryProvider, dailyTodoRepositoryProvider, habitTrackRepositoryProvider, efficiencyScoreRepositoryProvider, changeLogRepositoryProvider, goalChecklistItemRepositoryProvider);
  }

  public static DashboardViewModel newInstance(GoalRepository goalRepository,
      DailyTodoRepository dailyTodoRepository, HabitTrackRepository habitTrackRepository,
      EfficiencyScoreRepository efficiencyScoreRepository, ChangeLogRepository changeLogRepository,
      GoalChecklistItemRepository goalChecklistItemRepository) {
    return new DashboardViewModel(goalRepository, dailyTodoRepository, habitTrackRepository, efficiencyScoreRepository, changeLogRepository, goalChecklistItemRepository);
  }
}
