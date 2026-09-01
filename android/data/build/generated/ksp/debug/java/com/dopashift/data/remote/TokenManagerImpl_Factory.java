package com.dopashift.data.remote;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
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
public final class TokenManagerImpl_Factory implements Factory<TokenManagerImpl> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  private final Provider<AuthApiProvider> authApiProvider;

  private TokenManagerImpl_Factory(Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<AuthApiProvider> authApiProvider) {
    this.dataStoreProvider = dataStoreProvider;
    this.authApiProvider = authApiProvider;
  }

  @Override
  public TokenManagerImpl get() {
    return newInstance(dataStoreProvider.get(), authApiProvider.get());
  }

  public static TokenManagerImpl_Factory create(Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<AuthApiProvider> authApiProvider) {
    return new TokenManagerImpl_Factory(dataStoreProvider, authApiProvider);
  }

  public static TokenManagerImpl newInstance(DataStore<Preferences> dataStore,
      AuthApiProvider authApiProvider) {
    return new TokenManagerImpl(dataStore, authApiProvider);
  }
}
