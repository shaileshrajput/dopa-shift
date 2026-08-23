package com.dopashift.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.dopashift.data.remote.AuthApiProvider
import com.dopashift.data.remote.AuthInterceptor
import com.dopashift.data.remote.DopaShiftApi
import com.dopashift.data.remote.TokenManager
import com.dopashift.data.remote.TokenManagerImpl
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.authDataStore
    }

    /**
     * OkHttpClient without AuthInterceptor — used for token refresh calls
     * to avoid circular dependency.
     */
    @Provides
    @Singleton
    @AuthClient
    fun provideAuthOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Retrofit instance without AuthInterceptor for token refresh.
     */
    @Provides
    @Singleton
    @AuthClient
    fun provideAuthRetrofit(@AuthClient okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://localhost:8080/") // Configured at runtime via BuildConfig
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApiProvider(@AuthClient retrofit: Retrofit): AuthApiProvider {
        val authApi = retrofit.create(DopaShiftApi::class.java)
        return object : AuthApiProvider {
            override fun getAuthApi(): DopaShiftApi = authApi
        }
    }

    @Provides
    @Singleton
    fun provideTokenManager(
        dataStore: DataStore<Preferences>,
        authApiProvider: AuthApiProvider
    ): TokenManager {
        return TokenManagerImpl(dataStore, authApiProvider)
    }

    /**
     * Primary OkHttpClient with AuthInterceptor for authenticated API calls.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit {
        return Retrofit.Builder()
            .baseUrl("http://localhost:8080/") // Configured at runtime via BuildConfig
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideDopaShiftApi(retrofit: Retrofit): DopaShiftApi {
        return retrofit.create(DopaShiftApi::class.java)
    }
}
