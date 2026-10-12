package app.tormang.core.ui

import app.tormang.core.designsystem.l10n
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tormang.core.common.userMessage
import app.tormang.domain.model.*
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

open class ActionViewModel : ViewModel() {
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    fun dismissError() { error.value = null }
    fun launchAction(block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { error.value = e.userMessage() } finally { busy.value = false }
    }
}
@Composable fun ErrorBanner(message: String?, retry: (() -> Unit)? = null, dismiss: () -> Unit = {}) {
    if (message != null) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) { Text(message, color = MaterialTheme.colorScheme.onErrorContainer); Row { if (retry != null) TextButton(onClick = retry) { Text(l10n("Повторить")) }; TextButton(onClick = dismiss) { Text(l10n("Закрыть")) } } }
    }
}
@Composable fun EmptyState(title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.AutoStories, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (action != null) FilledTonalButton(onClick = onAction) { Text(action) }
    }
}
@Composable fun Cover(url: String, title: String, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(model = url.ifBlank { null }, contentDescription = title, contentScale = ContentScale.Crop, modifier = modifier,
        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) } },
        error = { Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MenuBook, null, Modifier.size(36.dp)) } } })
}
@Composable fun MangaCollection(items: List<LibraryItem>, grid: Boolean, onClick: (String) -> Unit, modifier: Modifier = Modifier) {
    if (grid) LazyVerticalGrid(columns = GridCells.Adaptive(148.dp), modifier = modifier, contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(items, key = { it.manga.id }) { item -> MangaTile(item, onClick) }
    } else LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.manga.id }) { item ->
            ListItem(headlineContent = { Text(item.manga.title) }, supportingContent = { Text(l10n("${item.manga.status} · ${item.unread} непрочитанных", "${item.manga.status} · ${item.unread} unread") + (item.lastChapter?.let { "\n$it" } ?: "")) }, leadingContent = { Cover(item.manga.cover, item.manga.title, Modifier.size(56.dp, 80.dp)) }, modifier = Modifier.clickable { onClick(item.manga.id) })
        }
    }
}
@Composable fun MangaTile(item: LibraryItem, onClick: (String) -> Unit) {
    Column(Modifier.clickable { onClick(item.manga.id) }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(shape = MaterialTheme.shapes.medium) {
            Box {
                Cover(item.manga.cover, item.manga.title, Modifier.fillMaxWidth().aspectRatio(0.7f))
                if (item.unread > 0) Badge(Modifier.align(Alignment.TopStart).padding(8.dp), containerColor = MaterialTheme.colorScheme.primary) { Text(item.unread.toString(), Modifier.padding(3.dp)) }
                if (item.manga.favorite) Icon(Icons.Outlined.Favorite, l10n("Избранное"), Modifier.align(Alignment.TopEnd).padding(8.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(item.manga.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        Text(item.lastChapter ?: item.manga.status, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null) {
    ListItem(headlineContent = { Text(title) }, supportingContent = subtitle?.let { { Text(it) } }, trailingContent = { Switch(checked, onChange) })
}
@Composable fun ChoiceRow(title: String, value: String, options: List<String>, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(value) }, trailingContent = { Icon(Icons.Outlined.ExpandMore, null) }, modifier = Modifier.clickable { open = true })
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = { Column { options.forEachIndexed { index, item -> TextButton(onClick = { onChange(index); open = false }, modifier = Modifier.fillMaxWidth()) { Text(item) } } } }, confirmButton = { TextButton(onClick = { open = false }) { Text(l10n("Закрыть")) } })
}
fun formatBytes(bytes: Long): String = if (bytes < 1024 * 1024) l10n("${bytes / 1024} КБ", "${bytes / 1024} KB") else l10n("%.1f МБ").format(bytes / 1048576.0)
fun formatTime(time: Long): String = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(time))
