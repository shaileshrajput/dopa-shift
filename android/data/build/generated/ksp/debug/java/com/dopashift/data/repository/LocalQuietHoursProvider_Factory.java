package com.dopashift.data.repository;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
    "KotlinInternalInJava"
})
public final class LocalQuietHoursProvider_Factory implements Factory<LocalQuietHoursProvider> {
  private final Provider<Context> contextProvider;

  public LocalQuietHoursProvider_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public LocalQuietHoursProvider get() {
    return newInstance(contextProvider.get());
  }

  public static LocalQuietHoursProvider_Factory create(Provider<Context> contextProvider) {
    return new LocalQuietHoursProvider_Factory(contextProvider);
  }

  public static LocalQuietHoursProvider newInstance(Context context) {
    return new LocalQuietHoursProvider(context);
  }
}
