package app.tormang

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.tormang.domain.repository.PreferencesRepository
import app.tormang.domain.repository.UpdateScheduler
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltAndroidApp class TormangApp : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var workers: HiltWorkerFactory
    @Inject lateinit var preferences: PreferencesRepository
    @Inject lateinit var scheduler: UpdateScheduler
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().setWorkerFactory(workers).build()
    override fun onCreate() {
        super.onCreate()
        app.tormang.core.designsystem.Strings.install(this)
        scope.launch { preferences.preferences.map { it.autoUpdate to it.wifiOnly }.distinctUntilChanged().collect { (enabled, wifi) -> scheduler.schedule(enabled, wifi) } }
    }
    override fun newImageLoader() = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.18).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(128L * 1024 * 1024).build() }
        .crossfade(120).build()
}
