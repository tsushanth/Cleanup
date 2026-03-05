package com.kreativekoala.cleanup.di

import com.kreativekoala.cleanup.data.remote.ArchiveApiService
import com.kreativekoala.cleanup.data.remote.SigV4Interceptor
import com.kreativekoala.cleanup.domain.service.ArchiveAuthService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideSigV4Interceptor(authService: ArchiveAuthService): SigV4Interceptor {
        return SigV4Interceptor { authService.getCredentials() }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(sigV4Interceptor: SigV4Interceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(sigV4Interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(ArchiveApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideArchiveApiService(retrofit: Retrofit): ArchiveApiService {
        return retrofit.create(ArchiveApiService::class.java)
    }
}
