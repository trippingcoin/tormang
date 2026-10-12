package app.tormang.data.local

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import app.tormang.domain.repository.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltWorker class DownloadWorker @AssistedInject constructor(@Assisted context: Context, @Assisted parameters: WorkerParameters, private val downloads: DownloadStore) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try { if (downloads.drain()) Result.success() else Result.retry() } catch (e: CancellationException) { throw e } catch (_: Exception) { if (runAttemptCount < 4) Result.retry() else Result.failure() }
}
@HiltWorker class LibraryUpdateWorker @AssistedInject constructor(@Assisted context: Context, @Assisted parameters: WorkerParameters, private val repository: MangaRepository, private val preferences: PreferencesRepository) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        val count = repository.refreshLibrary()
        if (count > 0 && preferences.preferences.first().notifications && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("updates", "Новые главы", NotificationManager.IMPORTANCE_DEFAULT))
            val intent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
            val pending = android.app.PendingIntent.getActivity(applicationContext, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            manager.notify(1, NotificationCompat.Builder(applicationContext, "updates").setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle("Tormang · новые главы").setContentText("Доступно новых глав: $count").setContentIntent(pending).setAutoCancel(true).build())
        }
        Result.success()
    } catch (e: CancellationException) { throw e } catch (e: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf("error" to e.message)) }
}
class Scheduler @Inject constructor(@ApplicationContext private val context: Context) : UpdateScheduler {
    override fun schedule(enabled: Boolean, wifiOnly: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) { work.cancelUniqueWork("library-updates"); return }
        val request = PeriodicWorkRequestBuilder<LibraryUpdateWorker>(12, TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()).build()
        work.enqueueUniquePeriodicWork("library-updates", ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    override fun refreshNow() { WorkManager.getInstance(context).enqueueUniqueWork("manual-update", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<LibraryUpdateWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()) }
}
