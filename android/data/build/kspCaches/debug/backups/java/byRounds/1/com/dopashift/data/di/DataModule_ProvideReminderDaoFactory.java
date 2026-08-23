package com.dopashift.data.di;

import com.dopashift.data.local.DopaShiftDatabase;
import com.dopashift.data.local.dao.ReminderDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
    "KotlinInternalInJava"
})
public final class DataModule_ProvideReminderDaoFactory implements Factory<ReminderDao> {
  private final Provider<DopaShiftDatabase> dbProvider;

  public DataModule_ProvideReminderDaoFactory(Provider<DopaShiftDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ReminderDao get() {
    return provideReminderDao(dbProvider.get());
  }

  public static DataModule_ProvideReminderDaoFactory create(
      Provider<DopaShiftDatabase> dbProvider) {
    return new DataModule_ProvideReminderDaoFactory(dbProvider);
  }

  public static ReminderDao provideReminderDao(DopaShiftDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideReminderDao(db));
  }
}
