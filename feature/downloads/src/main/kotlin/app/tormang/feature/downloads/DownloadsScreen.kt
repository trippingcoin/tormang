package app.tormang.feature.downloads

import app.tormang.core.designsystem.l10n
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tormang.core.designsystem.PageHeading
import app.tormang.core.ui.*
import app.tormang.domain.model.*
import app.tormang.domain.repository.DownloadsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel class DownloadsViewModel @Inject constructor(private val repository: DownloadsRepository) : ActionViewModel() {
    val downloads = repository.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun pause(id: String) = launchAction { repository.pause(id) }
    fun resume(id: String) = launchAction { repository.resume(id) }
    fun cancel(id: String) = launchAction { repository.cancel(id) }
    fun delete(id: String) = launchAction { repository.delete(id) }
}
@Composable fun DownloadsScreen(onRead: (String) -> Unit, vm: DownloadsViewModel = hiltViewModel()) {
    val items by vm.downloads.collectAsStateWithLifecycle(); val error by vm.error.collectAsStateWithLifecycle(); var deleting by remember { mutableStateOf<Download?>(null) }
    Column(Modifier.fillMaxSize()) {
        PageHeading(l10n("Загрузки"), l10n("${items.count { it.status == DownloadStatus.COMPLETE }} глав офлайн · ${formatBytes(items.sumOf { it.bytes })}", "${items.count { it.status == DownloadStatus.COMPLETE }} chapters offline · ${formatBytes(items.sumOf { it.bytes })}"))
        Text(l10n("Одна загрузка за раз. Ограничение Wi-Fi меняется в настройках."), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall)
        ErrorBanner(error, dismiss = vm::dismissError)
        if (items.isEmpty()) EmptyState(l10n("Возьмите истории с собой"), l10n("Скачайте главы на странице произведения, чтобы читать без сети."))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.chapterId }) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                val labels = mapOf(DownloadStatus.QUEUED to l10n("В очереди"), DownloadStatus.RUNNING to l10n("Загрузка"), DownloadStatus.PAUSED to l10n("Приостановлено"), DownloadStatus.FAILED to l10n("Ошибка"), DownloadStatus.COMPLETE to l10n("Готово"), DownloadStatus.CANCELED to l10n("Отменено"))
                Text(l10n("${labels[item.status]} · ${item.completed}/${item.total} · ${formatBytes(item.bytesPerSecond)}/с", "${labels[item.status]} · ${item.completed}/${item.total} · ${formatBytes(item.bytesPerSecond)}/s"), style = MaterialTheme.typography.bodySmall)
                if (item.total > 0) LinearProgressIndicator(progress = { item.completed.toFloat() / item.total }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                item.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row {
                    if (item.status == DownloadStatus.COMPLETE) TextButton(onClick = { onRead(item.chapterId) }) { Text(l10n("Читать")) }
                    else if (item.status in listOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED)) { TextButton(onClick = { vm.pause(item.chapterId) }) { Text(l10n("Пауза")) }; TextButton(onClick = { vm.cancel(item.chapterId) }) { Text(l10n("Отменить")) } }
                    else TextButton(onClick = { vm.resume(item.chapterId) }) { Text(if (item.status == DownloadStatus.FAILED) l10n("Повторить") else l10n("Возобновить")) }
                    IconButton(onClick = { deleting = item }) { Icon(Icons.Outlined.DeleteOutline, l10n("Удалить загрузку")) }
                }
            } } }
        }
    }
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(l10n("Удалить загруженные страницы?")) }, text = { Text(item.title) }, confirmButton = { TextButton(onClick = { vm.delete(item.chapterId); deleting = null }) { Text(l10n("Удалить")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(l10n("Отмена")) } }) }
}
