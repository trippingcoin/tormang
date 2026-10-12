package app.tormang

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import app.tormang.domain.repository.MangaRepository
import app.tormang.domain.repository.PreferencesRepository

@EntryPoint @InstallIn(SingletonComponent::class)
interface ReadingTestAccess { fun repository(): MangaRepository; fun preferences(): PreferencesRepository }

