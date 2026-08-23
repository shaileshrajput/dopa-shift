package com.dopashift.data.di;

import com.dopashift.data.remote.DopaShiftApi;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import retrofit2.Retrofit;

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
public final class NetworkModule_ProvideDopaShiftApiFactory implements Factory<DopaShiftApi> {
  private final Provider<Retrofit> retrofitProvider;

  public NetworkModule_ProvideDopaShiftApiFactory(Provider<Retrofit> retrofitProvider) {
    this.retrofitProvider = retrofitProvider;
  }

  @Override
  public DopaShiftApi get() {
    return provideDopaShiftApi(retrofitProvider.get());
  }

  public static NetworkModule_ProvideDopaShiftApiFactory create(
      Provider<Retrofit> retrofitProvider) {
    return new NetworkModule_ProvideDopaShiftApiFactory(retrofitProvider);
  }

  public static DopaShiftApi provideDopaShiftApi(Retrofit retrofit) {
    return Preconditions.checkNotNullFromProvides(NetworkModule.INSTANCE.provideDopaShiftApi(retrofit));
  }
}
