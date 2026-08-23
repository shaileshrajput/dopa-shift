package com.dopashift.interception.reminder;

import com.dopashift.domain.port.QuietHoursProvider;
import com.dopashift.domain.repository.ReminderRepository;
import com.dopashift.domain.service.ReminderEvaluationService;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.time.Clock;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
    "KotlinInternalInJava"
})
public final class ClientReminderEngine_Factory implements Factory<ClientReminderEngine> {
  private final Provider<ReminderRepository> reminderRepositoryProvider;

  private final Provider<ReminderEvaluationService> evaluationServiceProvider;

  private final Provider<QuietHoursProvider> quietHoursProvider;

  private final Provider<ReminderNotificationScheduler> notificationSchedulerProvider;

  private final Provider<Clock> clockProvider;

  public ClientReminderEngine_Factory(Provider<ReminderRepository> reminderRepositoryProvider,
      Provider<ReminderEvaluationService> evaluationServiceProvider,
      Provider<QuietHoursProvider> quietHoursProvider,
      Provider<ReminderNotificationScheduler> notificationSchedulerProvider,
      Provider<Clock> clockProvider) {
    this.reminderRepositoryProvider = reminderRepositoryProvider;
    this.evaluationServiceProvider = evaluationServiceProvider;
    this.quietHoursProvider = quietHoursProvider;
    this.notificationSchedulerProvider = notificationSchedulerProvider;
    this.clockProvider = clockProvider;
  }

  @Override
  public ClientReminderEngine get() {
    return newInstance(reminderRepositoryProvider.get(), evaluationServiceProvider.get(), quietHoursProvider.get(), notificationSchedulerProvider.get(), clockProvider.get());
  }

  public static ClientReminderEngine_Factory create(
      Provider<ReminderRepository> reminderRepositoryProvider,
      Provider<ReminderEvaluationService> evaluationServiceProvider,
      Provider<QuietHoursProvider> quietHoursProvider,
      Provider<ReminderNotificationScheduler> notificationSchedulerProvider,
      Provider<Clock> clockProvider) {
    return new ClientReminderEngine_Factory(reminderRepositoryProvider, evaluationServiceProvider, quietHoursProvider, notificationSchedulerProvider, clockProvider);
  }

  public static ClientReminderEngine newInstance(ReminderRepository reminderRepository,
      ReminderEvaluationService evaluationService, QuietHoursProvider quietHoursProvider,
      ReminderNotificationScheduler notificationScheduler, Clock clock) {
    return new ClientReminderEngine(reminderRepository, evaluationService, quietHoursProvider, notificationScheduler, clock);
  }
}
