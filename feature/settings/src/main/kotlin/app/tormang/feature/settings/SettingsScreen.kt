package app.tormang.feature.settings

import app.tormang.core.designsystem.l10n
import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tormang.core.designsystem.PageHeading
import app.tormang.core.ui.*
import app.tormang.domain.model.*
import app.tormang.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel class SettingsViewModel @Inject constructor(private val repository: PreferencesRepository, private val downloads: DownloadsRepository) : ActionViewModel() {
    val preferences = repository.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Preferences())
    val storage = MutableStateFlow(0L); val message = MutableStateFlow<String?>(null)
    init { launchAction { storage.value = downloads.storageBytes() } }
    fun update(transform: (Preferences) -> Preferences) = launchAction { repository.update(transform) }
    fun clearCache() = launchAction { downloads.clearCache(); message.value = l10n("Кэш очищен") }
}
@Composable fun SettingsScreen(onDownloads: () -> Unit, onBackup: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val prefs by vm.preferences.collectAsStateWithLifecycle(); val bytes by vm.storage.collectAsStateWithLifecycle(); val error by vm.error.collectAsStateWithLifecycle(); val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var notificationGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) {
        try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); vm.update { it.copy(downloadTree = uri.toString()) } } catch (e: SecurityException) { vm.error.value = l10n("Нет доступа к выбранной папке") }
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> notificationGranted = granted; vm.update { it.copy(notifications = granted) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { PageHeading(l10n("Ваш Tormang"), l10n("Спокойное пространство для чтения")) }
        item { ListItem(headlineContent = { Text(l10n("Загрузки")) }, supportingContent = { Text(l10n("Очередь и офлайн-чтение · ${formatBytes(bytes)}", "Queue and offline reading · ${formatBytes(bytes)}")) }, leadingContent = { Icon(Icons.Outlined.Download, null) }, modifier = Modifier.clickable(onClick = onDownloads)) }
        item { ListItem(headlineContent = { Text(l10n("Резервная копия")) }, supportingContent = { Text(l10n("Экспорт и восстановление данных")) }, leadingContent = { Icon(Icons.Outlined.Backup, null) }, modifier = Modifier.clickable(onClick = onBackup)) }
        item { Section(l10n("Оформление")) }
        item { val labels = listOf(l10n("Системная"), l10n("Светлая"), l10n("Тёмная"), "AMOLED"); ChoiceRow(l10n("Тема"), labels[prefs.theme.ordinal], labels) { index -> vm.update { it.copy(theme = ThemeMode.entries[index]) } } }
        item { ToggleRow(l10n("Динамические цвета"), prefs.dynamicColors, { checked -> vm.update { it.copy(dynamicColors = checked) } }, l10n("Палитра устройства на Android 12+")) }
        item { ChoiceRow("Language / Язык", prefs.language, listOf("System / Системный", "Русский", "English")) { index -> vm.update { it.copy(language = listOf("system", "ru", "en")[index]) } } }
        item { Section(l10n("Читалка")) }
        item { val labels = listOf(l10n("Вертикально"), l10n("Горизонтально"), l10n("Справа налево"), l10n("Слева направо"), l10n("Вебтун")); ChoiceRow(l10n("Режим по умолчанию"), labels[prefs.readerMode.ordinal], labels) { index -> vm.update { it.copy(readerMode = ReaderMode.entries[index]) } } }
        item { ToggleRow(l10n("Переход к следующей главе"), prefs.autoNext, { checked -> vm.update { it.copy(autoNext = checked) } }) }
        item { Section(l10n("Библиотека и уведомления")) }
        item { ToggleRow(l10n("Автоматическое обновление"), prefs.autoUpdate, { checked -> vm.update { it.copy(autoUpdate = checked) } }, l10n("Примерно каждые 12 часов с учётом ограничений Android")) }
        item { ToggleRow(l10n("Уведомления о новых главах"), prefs.notifications && notificationGranted, { checked -> if (checked && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.update { it.copy(notifications = checked) } }) }
        item { Section(l10n("Загрузки и хранилище")) }
        item { ToggleRow(l10n("Только Wi-Fi"), prefs.wifiOnly, { checked -> vm.update { it.copy(wifiOnly = checked) } }, l10n("Для новых заданий загрузки и фоновых обновлений")) }
        item { ListItem(headlineContent = { Text(l10n("Папка новых загрузок")) }, supportingContent = { Text(if (prefs.downloadTree.isBlank()) l10n("Внутреннее хранилище приложения") else prefs.downloadTree) }, modifier = Modifier.clickable { folder.launch(null) }) }
        item { if (prefs.downloadTree.isNotBlank()) TextButton(onClick = { vm.update { it.copy(downloadTree = "") } }, modifier = Modifier.padding(horizontal = 16.dp)) { Text(l10n("Использовать внутреннее хранилище")) } }
        item { ListItem(headlineContent = { Text(l10n("Очистить кэш")) }, supportingContent = { Text(l10n("Скачанные главы сохранятся")) }, modifier = Modifier.clickable { vm.clearCache() }) }
        item { Section(l10n("Приватность")) }
        item { ToggleRow(l10n("Не записывать историю"), prefs.incognito, { checked -> vm.update { it.copy(incognito = checked) } }, l10n("Прогресс глав продолжит сохраняться")) }
        item { Text(l10n("Без рекламы, аналитики и обязательного аккаунта. Сетевые запросы отправляются только выбранным источникам."), Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall) }
        item { ErrorBanner(error, dismiss = vm::dismissError); message?.let { Text(it, Modifier.padding(24.dp)) } }
        item { Text("TORMANG  /  0.1.0", Modifier.padding(24.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    }
}
@Composable private fun Section(title: String) { HorizontalDivider(Modifier.padding(top = 16.dp)); Text(title, Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
