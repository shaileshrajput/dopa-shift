package com.dopashift.data.repository;

import com.dopashift.data.local.dao.HabitTrackDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
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
public final class HabitTrackRepositoryImpl_Factory implements Factory<HabitTrackRepositoryImpl> {
  private final Provider<HabitTrackDao> habitTrackDaoProvider;

  private HabitTrackRepositoryImpl_Factory(Provider<HabitTrackDao> habitTrackDaoProvider) {
    this.habitTrackDaoProvider = habitTrackDaoProvider;
  }

  @Override
  public HabitTrackRepositoryImpl get() {
    return newInstance(habitTrackDaoProvider.get());
  }

  public static HabitTrackRepositoryImpl_Factory create(
      Provider<HabitTrackDao> habitTrackDaoProvider) {
    return new HabitTrackRepositoryImpl_Factory(habitTrackDaoProvider);
  }

  public static HabitTrackRepositoryImpl newInstance(HabitTrackDao habitTrackDao) {
    return new HabitTrackRepositoryImpl(habitTrackDao);
  }
}
