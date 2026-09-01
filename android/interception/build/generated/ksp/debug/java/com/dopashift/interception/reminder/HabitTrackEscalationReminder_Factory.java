package com.dopashift.interception.reminder;

import android.content.Context;
import com.dopashift.domain.port.QuietHoursProvider;
import com.dopashift.domain.repository.HabitTrackRepository;
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
public final class HabitTrackEscalationReminder_Factory implements Factory<HabitTrackEscalationReminder> {
  private final Provider<Context> contextProvider;

  private final Provider<HabitTrackRepository> habitTrackRepositoryProvider;

  private final Provider<QuietHoursProvider> quietHoursProvider;

  private final Provider<Clock> clockProvider;

  private HabitTrackEscalationReminder_Factory(Provider<Context> contextProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider,
      Provider<QuietHoursProvider> quietHoursProvider, Provider<Clock> clockProvider) {
    this.contextProvider = contextProvider;
    this.habitTrackRepositoryProvider = habitTrackRepositoryProvider;
    this.quietHoursProvider = quietHoursProvider;
    this.clockProvider = clockProvider;
  }

  @Override
  public HabitTrackEscalationReminder get() {
    return newInstance(contextProvider.get(), habitTrackRepositoryProvider.get(), quietHoursProvider.get(), clockProvider.get());
  }

  public static HabitTrackEscalationReminder_Factory create(Provider<Context> contextProvider,
      Provider<HabitTrackRepository> habitTrackRepositoryProvider,
      Provider<QuietHoursProvider> quietHoursProvider, Provider<Clock> clockProvider) {
    return new HabitTrackEscalationReminder_Factory(contextProvider, habitTrackRepositoryProvider, quietHoursProvider, clockProvider);
  }

  public static HabitTrackEscalationReminder newInstance(Context context,
      HabitTrackRepository habitTrackRepository, QuietHoursProvider quietHoursProvider,
      Clock clock) {
    return new HabitTrackEscalationReminder(context, habitTrackRepository, quietHoursProvider, clock);
  }
}
