package com.dopashift.data.repository;

import com.dopashift.data.local.dao.ReminderDao;
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
public final class ReminderRepositoryImpl_Factory implements Factory<ReminderRepositoryImpl> {
  private final Provider<ReminderDao> reminderDaoProvider;

  public ReminderRepositoryImpl_Factory(Provider<ReminderDao> reminderDaoProvider) {
    this.reminderDaoProvider = reminderDaoProvider;
  }

  @Override
  public ReminderRepositoryImpl get() {
    return newInstance(reminderDaoProvider.get());
  }

  public static ReminderRepositoryImpl_Factory create(Provider<ReminderDao> reminderDaoProvider) {
    return new ReminderRepositoryImpl_Factory(reminderDaoProvider);
  }

  public static ReminderRepositoryImpl newInstance(ReminderDao reminderDao) {
    return new ReminderRepositoryImpl(reminderDao);
  }
}
