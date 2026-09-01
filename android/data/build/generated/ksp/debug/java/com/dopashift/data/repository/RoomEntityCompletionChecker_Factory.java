package com.dopashift.data.repository;

import com.dopashift.data.local.dao.DailyTodoDao;
import com.dopashift.data.local.dao.GoalChecklistDao;
import com.dopashift.data.local.dao.HabitTrackDao;
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
public final class RoomEntityCompletionChecker_Factory implements Factory<RoomEntityCompletionChecker> {
  private final Provider<DailyTodoDao> dailyTodoDaoProvider;

  private final Provider<GoalChecklistDao> goalChecklistDaoProvider;

  private final Provider<HabitTrackDao> habitTrackDaoProvider;

  private RoomEntityCompletionChecker_Factory(Provider<DailyTodoDao> dailyTodoDaoProvider,
      Provider<GoalChecklistDao> goalChecklistDaoProvider,
      Provider<HabitTrackDao> habitTrackDaoProvider) {
    this.dailyTodoDaoProvider = dailyTodoDaoProvider;
    this.goalChecklistDaoProvider = goalChecklistDaoProvider;
    this.habitTrackDaoProvider = habitTrackDaoProvider;
  }

  @Override
  public RoomEntityCompletionChecker get() {
    return newInstance(dailyTodoDaoProvider.get(), goalChecklistDaoProvider.get(), habitTrackDaoProvider.get());
  }

  public static RoomEntityCompletionChecker_Factory create(
      Provider<DailyTodoDao> dailyTodoDaoProvider,
      Provider<GoalChecklistDao> goalChecklistDaoProvider,
      Provider<HabitTrackDao> habitTrackDaoProvider) {
    return new RoomEntityCompletionChecker_Factory(dailyTodoDaoProvider, goalChecklistDaoProvider, habitTrackDaoProvider);
  }

  public static RoomEntityCompletionChecker newInstance(DailyTodoDao dailyTodoDao,
      GoalChecklistDao goalChecklistDao, HabitTrackDao habitTrackDao) {
    return new RoomEntityCompletionChecker(dailyTodoDao, goalChecklistDao, habitTrackDao);
  }
}
