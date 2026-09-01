package com.dopashift.interception.reminder;

import android.content.Context;
import com.dopashift.domain.repository.DailyTodoRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class InterceptionTodoReminderNotifier_Factory implements Factory<InterceptionTodoReminderNotifier> {
  private final Provider<Context> contextProvider;

  private final Provider<DailyTodoRepository> dailyTodoRepositoryProvider;

  private InterceptionTodoReminderNotifier_Factory(Provider<Context> contextProvider,
      Provider<DailyTodoRepository> dailyTodoRepositoryProvider) {
    this.contextProvider = contextProvider;
    this.dailyTodoRepositoryProvider = dailyTodoRepositoryProvider;
  }

  @Override
  public InterceptionTodoReminderNotifier get() {
    return newInstance(contextProvider.get(), dailyTodoRepositoryProvider.get());
  }

  public static InterceptionTodoReminderNotifier_Factory create(Provider<Context> contextProvider,
      Provider<DailyTodoRepository> dailyTodoRepositoryProvider) {
    return new InterceptionTodoReminderNotifier_Factory(contextProvider, dailyTodoRepositoryProvider);
  }

  public static InterceptionTodoReminderNotifier newInstance(Context context,
      DailyTodoRepository dailyTodoRepository) {
    return new InterceptionTodoReminderNotifier(context, dailyTodoRepository);
  }
}
