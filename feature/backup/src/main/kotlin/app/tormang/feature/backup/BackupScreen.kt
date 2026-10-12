package app.tormang.feature.backup

import app.tormang.core.designsystem.l10n
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tormang.core.designsystem.PageHeading
import app.tormang.core.ui.*
import app.tormang.domain.model.BackupPreview
import app.tormang.domain.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel class BackupViewModel @Inject constructor(private val repository: BackupRepository) : ActionViewModel() {
    val preview = MutableStateFlow<BackupPreview?>(null); val message = MutableStateFlow<String?>(null)
    fun export(uri: String) = launchAction { repository.export(uri); message.value = l10n("Резервная копия сохранена") }
    fun preview(uri: String) = launchAction { preview.value = repository.preview(uri) }
    fun restore() = launchAction { preview.value?.let { repository.restore(it) }; preview.value = null; message.value = l10n("Данные восстановлены") }
}
@Composable fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val preview by vm.preview.collectAsStateWithLifecycle(); val message by vm.message.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle(); val error by vm.error.collectAsStateWithLifecycle()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { vm.export(it.toString()) } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { vm.preview(it.toString()) } }
    Column(Modifier.fillMaxSize()) {
        PageHeading(l10n("Резервная копия"), l10n("Ваши истории остаются с вами"))
        Text(l10n("Библиотека, категории, история, позиции чтения, дневник, источники и настройки. Формат Tormang v1."), Modifier.padding(24.dp))
        Text(l10n("Скачанные страницы и локальные архивы переносятся отдельно. Копии Mihon / Tachiyomi имеют другой формат."), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = { export.launch("tormang-${java.time.LocalDate.now()}.json") }, enabled = !busy) { Text(l10n("Экспорт")) }; OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy) { Text(l10n("Импорт")) } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ErrorBanner(error, dismiss = vm::dismissError)
        message?.let { Text(it, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.primary) }
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 16.dp)) { Text(l10n("Назад")) }
    }
    preview?.let { p -> AlertDialog(onDismissRequest = { vm.preview.value = null }, title = { Text(l10n("Восстановить данные?")) }, text = { Text(l10n("Произведений: ${p.backup.manga.size}\nГлав: ${p.backup.chapters.size}\nКатегорий: ${p.backup.categories.size}\nЗаписей истории: ${p.backup.history.size}\nЛокальных книг для повторного импорта: ${p.missingLocalBooks}\n\nСовпадающие записи будут заменены. Остальная библиотека сохранится.", "Titles: ${p.backup.manga.size}\nChapters: ${p.backup.chapters.size}\nCategories: ${p.backup.categories.size}\nHistory entries: ${p.backup.history.size}\nLocal books to reimport: ${p.missingLocalBooks}\n\nMatching entries will be replaced. Other library entries will be preserved.")) }, confirmButton = { TextButton(onClick = { vm.restore() }, enabled = !busy) { Text(l10n("Восстановить")) } }, dismissButton = { TextButton(onClick = { vm.preview.value = null }) { Text(l10n("Отмена")) } }) }
}
