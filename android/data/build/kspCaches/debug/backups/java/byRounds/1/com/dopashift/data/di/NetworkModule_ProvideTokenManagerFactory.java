package com.dopashift.data.di;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import com.dopashift.data.remote.AuthApiProvider;
import com.dopashift.data.remote.TokenManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class NetworkModule_ProvideTokenManagerFactory implements Factory<TokenManager> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  private final Provider<AuthApiProvider> authApiProvider;

  public NetworkModule_ProvideTokenManagerFactory(
      Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<AuthApiProvider> authApiProvider) {
    this.dataStoreProvider = dataStoreProvider;
    this.authApiProvider = authApiProvider;
  }

  @Override
  public TokenManager get() {
    return provideTokenManager(dataStoreProvider.get(), authApiProvider.get());
  }

  public static NetworkModule_ProvideTokenManagerFactory create(
      Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<AuthApiProvider> authApiProvider) {
    return new NetworkModule_ProvideTokenManagerFactory(dataStoreProvider, authApiProvider);
  }

  public static TokenManager provideTokenManager(DataStore<Preferences> dataStore,
      AuthApiProvider authApiProvider) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideTokenManager(dataStore, authApiProvider));
  }
}
