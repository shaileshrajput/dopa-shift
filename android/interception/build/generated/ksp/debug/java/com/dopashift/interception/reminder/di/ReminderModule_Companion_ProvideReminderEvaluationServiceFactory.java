package com.dopashift.interception.reminder.di;

import com.dopashift.domain.port.EntityCompletionChecker;
import com.dopashift.domain.service.ReminderEvaluationService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.time.Clock;
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
public final class ReminderModule_Companion_ProvideReminderEvaluationServiceFactory implements Factory<ReminderEvaluationService> {
  private final Provider<Clock> clockProvider;

  private final Provider<EntityCompletionChecker> entityCompletionCheckerProvider;

  private ReminderModule_Companion_ProvideReminderEvaluationServiceFactory(
      Provider<Clock> clockProvider,
      Provider<EntityCompletionChecker> entityCompletionCheckerProvider) {
    this.clockProvider = clockProvider;
    this.entityCompletionCheckerProvider = entityCompletionCheckerProvider;
  }

  @Override
  public ReminderEvaluationService get() {
    return provideReminderEvaluationService(clockProvider.get(), entityCompletionCheckerProvider.get());
  }

  public static ReminderModule_Companion_ProvideReminderEvaluationServiceFactory create(
      Provider<Clock> clockProvider,
      Provider<EntityCompletionChecker> entityCompletionCheckerProvider) {
    return new ReminderModule_Companion_ProvideReminderEvaluationServiceFactory(clockProvider, entityCompletionCheckerProvider);
  }

  public static ReminderEvaluationService provideReminderEvaluationService(Clock clock,
      EntityCompletionChecker entityCompletionChecker) {
    return Preconditions.checkNotNullFromProvides(ReminderModule.Companion.provideReminderEvaluationService(clock, entityCompletionChecker));
  }
}
