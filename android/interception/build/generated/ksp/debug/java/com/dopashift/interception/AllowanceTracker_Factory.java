package com.dopashift.interception;

import com.dopashift.data.repository.LocalTelemetryRepository;
import com.dopashift.domain.repository.InterceptionRuleRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class AllowanceTracker_Factory implements Factory<AllowanceTracker> {
  private final Provider<InterceptionRuleRepository> ruleRepositoryProvider;

  private final Provider<LocalTelemetryRepository> telemetryRepositoryProvider;

  private final Provider<Clock> clockProvider;

  private AllowanceTracker_Factory(Provider<InterceptionRuleRepository> ruleRepositoryProvider,
      Provider<LocalTelemetryRepository> telemetryRepositoryProvider,
      Provider<Clock> clockProvider) {
    this.ruleRepositoryProvider = ruleRepositoryProvider;
    this.telemetryRepositoryProvider = telemetryRepositoryProvider;
    this.clockProvider = clockProvider;
  }

  @Override
  public AllowanceTracker get() {
    return newInstance(ruleRepositoryProvider.get(), telemetryRepositoryProvider.get(), clockProvider.get());
  }

  public static AllowanceTracker_Factory create(
      Provider<InterceptionRuleRepository> ruleRepositoryProvider,
      Provider<LocalTelemetryRepository> telemetryRepositoryProvider,
      Provider<Clock> clockProvider) {
    return new AllowanceTracker_Factory(ruleRepositoryProvider, telemetryRepositoryProvider, clockProvider);
  }

  public static AllowanceTracker newInstance(InterceptionRuleRepository ruleRepository,
      LocalTelemetryRepository telemetryRepository, Clock clock) {
    return new AllowanceTracker(ruleRepository, telemetryRepository, clock);
  }
}
