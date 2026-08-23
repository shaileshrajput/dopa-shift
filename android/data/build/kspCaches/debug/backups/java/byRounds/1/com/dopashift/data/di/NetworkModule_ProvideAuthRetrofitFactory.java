package com.dopashift.data.di;

import com.squareup.moshi.Moshi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;
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
public final class NetworkModule_ProvideAuthRetrofitFactory implements Factory<Retrofit> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  private final Provider<Moshi> moshiProvider;

  public NetworkModule_ProvideAuthRetrofitFactory(Provider<OkHttpClient> okHttpClientProvider,
      Provider<Moshi> moshiProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
    this.moshiProvider = moshiProvider;
  }

  @Override
  public Retrofit get() {
    return provideAuthRetrofit(okHttpClientProvider.get(), moshiProvider.get());
  }

  public static NetworkModule_ProvideAuthRetrofitFactory create(
      Provider<OkHttpClient> okHttpClientProvider, Provider<Moshi> moshiProvider) {
    return new NetworkModule_ProvideAuthRetrofitFactory(okHttpClientProvider, moshiProvider);
  }

  public static Retrofit provideAuthRetrofit(OkHttpClient okHttpClient, Moshi moshi) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideAuthRetrofit(okHttpClient, moshi));
  }
}
