package com.dopashift.data.di;

import android.content.Context;
import com.dopashift.data.local.DopaShiftDatabase;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DataModule_ProvideDatabaseFactory implements Factory<DopaShiftDatabase> {
  private final Provider<Context> contextProvider;

  private DataModule_ProvideDatabaseFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public DopaShiftDatabase get() {
    return provideDatabase(contextProvider.get());
  }

  public static DataModule_ProvideDatabaseFactory create(Provider<Context> contextProvider) {
    return new DataModule_ProvideDatabaseFactory(contextProvider);
  }

  public static DopaShiftDatabase provideDatabase(Context context) {
    return Preconditions.checkNotNullFromProvides(DataModule.INSTANCE.provideDatabase(context));
  }
}
