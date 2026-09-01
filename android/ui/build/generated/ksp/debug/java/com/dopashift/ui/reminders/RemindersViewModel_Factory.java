package com.dopashift.ui.reminders;

import com.dopashift.domain.repository.ReminderRepository;
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
public final class RemindersViewModel_Factory implements Factory<RemindersViewModel> {
  private final Provider<ReminderRepository> reminderRepositoryProvider;

  private RemindersViewModel_Factory(Provider<ReminderRepository> reminderRepositoryProvider) {
    this.reminderRepositoryProvider = reminderRepositoryProvider;
  }

  @Override
  public RemindersViewModel get() {
    return newInstance(reminderRepositoryProvider.get());
  }

  public static RemindersViewModel_Factory create(
      Provider<ReminderRepository> reminderRepositoryProvider) {
    return new RemindersViewModel_Factory(reminderRepositoryProvider);
  }

  public static RemindersViewModel newInstance(ReminderRepository reminderRepository) {
    return new RemindersViewModel(reminderRepository);
  }
}
