package com.dopashift.data.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import okhttp3.OkHttpClient;

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
public final class NetworkModule_ProvideAuthOkHttpClientFactory implements Factory<OkHttpClient> {
  @Override
  public OkHttpClient get() {
    return provideAuthOkHttpClient();
  }

  public static NetworkModule_ProvideAuthOkHttpClientFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static OkHttpClient provideAuthOkHttpClient() {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideAuthOkHttpClient());
  }

  private static final class InstanceHolder {
    private static final NetworkModule_ProvideAuthOkHttpClientFactory INSTANCE = new NetworkModule_ProvideAuthOkHttpClientFactory();
  }
}
