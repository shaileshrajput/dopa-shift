package com.dopashift.data.di;

import com.dopashift.data.remote.AuthApiProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import retrofit2.Retrofit;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("com.dopashift.data.di.AuthClient")
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
public final class NetworkModule_ProvideAuthApiProviderFactory implements Factory<AuthApiProvider> {
  private final Provider<Retrofit> retrofitProvider;

  public NetworkModule_ProvideAuthApiProviderFactory(Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public AuthApiProvider get() {
    return provideAuthApiProvider(retrofitProvider.get());
  }

  public static NetworkModule_ProvideAuthApiProviderFactory create(
      Provider<Retrofit> retrofitProvider) {
    return new NetworkModule_ProvideAuthApiProviderFactory(retrofitProvider);
  }

  public static AuthApiProvider provideAuthApiProvider(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideAuthApiProvider(retrofit));
  }
}
