package com.dopashift.data.repository;

import com.dopashift.data.local.dao.TelemetryDao;
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
public final class LocalTelemetryRepository_Factory implements Factory<LocalTelemetryRepository> {
  private final Provider<TelemetryDao> telemetryDaoProvider;

  private LocalTelemetryRepository_Factory(Provider<TelemetryDao> telemetryDaoProvider) {
    this.telemetryDaoProvider = telemetryDaoProvider;
  }

  @Override
  public LocalTelemetryRepository get() {
    return newInstance(telemetryDaoProvider.get());
  }

  public static LocalTelemetryRepository_Factory create(
      Provider<TelemetryDao> telemetryDaoProvider) {
    return new LocalTelemetryRepository_Factory(telemetryDaoProvider);
  }

  public static LocalTelemetryRepository newInstance(TelemetryDao telemetryDao) {
    return new LocalTelemetryRepository(telemetryDao);
  }
}
