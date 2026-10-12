package app.tormang.feature.tracking

import app.tormang.core.designsystem.l10n
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tormang.core.designsystem.PageHeading
import app.tormang.core.ui.*
import app.tormang.domain.model.Tracking
import app.tormang.domain.repository.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel class TrackingViewModel @Inject constructor(handle: SavedStateHandle, private val repository: TrackingRepository) : ActionViewModel() {
    val id: String = checkNotNull(handle["mangaId"])
    val tracking = repository.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun save(value: Tracking) = launchAction { repository.save(value) }
}
@Composable fun TrackingScreen(onBack: () -> Unit, vm: TrackingViewModel = hiltViewModel()) {
    val saved by vm.tracking.collectAsStateWithLifecycle(); val error by vm.error.collectAsStateWithLifecycle()
    var status by remember(saved) { mutableStateOf(saved?.status ?: "Reading") }; var score by remember(saved) { mutableFloatStateOf((saved?.score ?: 0).toFloat()) }; var notes by remember(saved) { mutableStateOf(saved?.notes.orEmpty()) }
    Column(Modifier.fillMaxSize()) {
        PageHeading(l10n("Дневник чтения"), l10n("Личный трекинг на устройстве"))
        Text(l10n("Прочитанные главы обновляются автоматически. Записи входят в резервную копию."), Modifier.padding(24.dp))
        ChoiceRow(l10n("Статус"), status, listOf("Reading", "Planned", "Completed", "On hold", "Dropped")) { status = listOf("Reading", "Planned", "Completed", "On hold", "Dropped")[it] }
        Text(l10n("Оценка: ${score.toInt()} / 10", "Score: ${score.toInt()} / 10"), Modifier.padding(horizontal = 24.dp))
        Slider(score, { score = it }, Modifier.padding(horizontal = 24.dp), valueRange = 0f..10f, steps = 9)
        OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth().padding(24.dp), label = { Text(l10n("Заметки")) }, minLines = 3)
        ErrorBanner(error, dismiss = vm::dismissError)
        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = { vm.save(Tracking(vm.id, status, score.toInt(), saved?.chaptersRead ?: 0, notes)) }) { Text(l10n("Сохранить")) }; TextButton(onClick = onBack) { Text(l10n("Назад")) } }
        if (saved != null) Text(l10n("Сохранено · ${saved?.chaptersRead} глав", "Saved · ${saved?.chaptersRead} chapters"), Modifier.padding(horizontal = 24.dp))
    }
}
