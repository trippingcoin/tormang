package app.tormang.core.network

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Serializable data class XkcdComic(val num: Int, val title: String, val img: String, val alt: String, val year: String, val month: String, val day: String)
interface XkcdApi {
    @GET("info.0.json") suspend fun latest(): XkcdComic
    @GET("{id}/info.0.json") suspend fun comic(@Path("id") id: Int): XkcdComic
}
@Module @InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun client(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 32L * 1024 * 1024))
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "Tormang/0.1 Android reader").build()) }
        .build()
    @Provides @Singleton fun api(client: OkHttpClient): XkcdApi = Retrofit.Builder().baseUrl("https://xkcd.com/")
        .client(client).addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
        .build().create(XkcdApi::class.java)
}
