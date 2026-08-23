package com.dopashift.interception.reminder;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class AlarmManagerReminderScheduler_Factory implements Factory<AlarmManagerReminderScheduler> {
  private final Provider<Context> contextProvider;

  public AlarmManagerReminderScheduler_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public AlarmManagerReminderScheduler get() {
    return newInstance(contextProvider.get());
  }

  public static AlarmManagerReminderScheduler_Factory create(Provider<Context> contextProvider) {
    return new AlarmManagerReminderScheduler_Factory(contextProvider);
  }

  public static AlarmManagerReminderScheduler newInstance(Context context) {
    return new AlarmManagerReminderScheduler(context);
  }
}
