package app.tormang.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tormang.domain.model.Preferences
import app.tormang.domain.repository.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settings by preferencesDataStore("tormang-settings")
@Singleton class PreferencesStore @Inject constructor(@ApplicationContext private val context: Context) : PreferencesRepository {
    private val key = stringPreferencesKey("preferences-v1")
    private val json = Json { ignoreUnknownKeys = true }
    override val preferences = context.settings.data.map { data -> data[key]?.let { json.decodeFromString<Preferences>(it) } ?: Preferences() }
    override suspend fun update(transform: (Preferences) -> Preferences) {
        context.settings.edit { data ->
            val old = data[key]?.let { json.decodeFromString<Preferences>(it) } ?: Preferences()
            data[key] = json.encodeToString(transform(old))
        }
    }
}
@Module @InstallIn(SingletonComponent::class)
abstract class PreferencesModule { @Binds abstract fun bind(impl: PreferencesStore): PreferencesRepository }
