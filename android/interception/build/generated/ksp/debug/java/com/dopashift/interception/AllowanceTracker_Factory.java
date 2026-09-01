package com.dopashift.interception;

import com.dopashift.data.local.dao.InterceptionRuleDao;
import com.dopashift.data.repository.LocalTelemetryRepository;
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
  private final Provider<InterceptionRuleDao> interceptionRuleDaoProvider;

  private final Provider<LocalTelemetryRepository> telemetryRepositoryProvider;

  private final Provider<Clock> clockProvider;

  private AllowanceTracker_Factory(Provider<InterceptionRuleDao> interceptionRuleDaoProvider,
      Provider<LocalTelemetryRepository> telemetryRepositoryProvider,
      Provider<Clock> clockProvider) {
    this.interceptionRuleDaoProvider = interceptionRuleDaoProvider;
    this.telemetryRepositoryProvider = telemetryRepositoryProvider;
    this.clockProvider = clockProvider;
  }

  @Override
  public AllowanceTracker get() {
    return newInstance(interceptionRuleDaoProvider.get(), telemetryRepositoryProvider.get(), clockProvider.get());
  }

  public static AllowanceTracker_Factory create(
      Provider<InterceptionRuleDao> interceptionRuleDaoProvider,
      Provider<LocalTelemetryRepository> telemetryRepositoryProvider,
      Provider<Clock> clockProvider) {
    return new AllowanceTracker_Factory(interceptionRuleDaoProvider, telemetryRepositoryProvider, clockProvider);
  }

  public static AllowanceTracker newInstance(InterceptionRuleDao interceptionRuleDao,
      LocalTelemetryRepository telemetryRepository, Clock clock) {
    return new AllowanceTracker(interceptionRuleDao, telemetryRepository, clock);
  }
}
