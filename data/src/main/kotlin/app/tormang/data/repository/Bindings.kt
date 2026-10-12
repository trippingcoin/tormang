package app.tormang.data.repository

import app.tormang.data.local.*
import app.tormang.data.remote.Sources
import app.tormang.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module @InstallIn(SingletonComponent::class)
abstract class Bindings {
    @Binds abstract fun manga(impl: LibraryRepository): MangaRepository
    @Binds abstract fun sources(impl: Sources): SourceRepository
    @Binds abstract fun downloads(impl: DownloadStore): DownloadsRepository
    @Binds abstract fun backups(impl: Backups): BackupRepository
    @Binds abstract fun tracking(impl: TrackingStore): TrackingRepository
    @Binds abstract fun scheduler(impl: Scheduler): UpdateScheduler
}
