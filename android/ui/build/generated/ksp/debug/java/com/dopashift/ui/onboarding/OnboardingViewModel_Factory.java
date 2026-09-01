package com.dopashift.ui.onboarding;

import com.dopashift.domain.repository.GoalRepository;
import com.dopashift.domain.repository.HabitTrackRepository;
import com.dopashift.domain.repository.UserPreferencesRepository;
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
public final class OnboardingViewModel_Factory implements Factory<OnboardingViewModel> {
  private final Provider<GoalRepository> goalRepositoryProvider;

  private final Provider<HabitTrackRepository> habitTrackRepositoryProvider;

  private final Provider<UserPreferencesRepository> userPreferencesRepositoryProvider;

  private OnboardingViewModel_Factory(Provider<GoalRepository> goalRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider,
      Provider<UserPreferencesRepository> userPreferencesRepositoryProvider) {
    this.goalRepositoryProvider = goalRepositoryProvider;
    this.habitTrackRepositoryProvider = habitTrackRepositoryProvider;
    this.userPreferencesRepositoryProvider = userPreferencesRepositoryProvider;
  }

  @Override
  public OnboardingViewModel get() {
    return newInstance(goalRepositoryProvider.get(), habitTrackRepositoryProvider.get(), userPreferencesRepositoryProvider.get());
  }

  public static OnboardingViewModel_Factory create(Provider<GoalRepository> goalRepositoryProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider,
      Provider<UserPreferencesRepository> userPreferencesRepositoryProvider) {
    return new OnboardingViewModel_Factory(goalRepositoryProvider, habitTrackRepositoryProvider, userPreferencesRepositoryProvider);
  }

  public static OnboardingViewModel newInstance(GoalRepository goalRepository,
      HabitTrackRepository habitTrackRepository,
      UserPreferencesRepository userPreferencesRepository) {
    return new OnboardingViewModel(goalRepository, habitTrackRepository, userPreferencesRepository);
  }
}
