package app.tormang

import app.tormang.core.designsystem.l10n
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import app.tormang.core.designsystem.TormangTheme
import app.tormang.domain.model.Preferences
import app.tormang.domain.repository.PreferencesRepository
import app.tormang.feature.library.LibraryScreen
import app.tormang.feature.browse.BrowseScreen
import app.tormang.feature.manga.MangaScreen
import app.tormang.feature.reader.ReaderScreen
import app.tormang.feature.downloads.DownloadsScreen
import app.tormang.feature.history.HistoryScreen
import app.tormang.feature.updates.UpdatesScreen
import app.tormang.feature.settings.SettingsScreen
import app.tormang.feature.tracking.TrackingScreen
import app.tormang.feature.backup.BackupScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    @Inject lateinit var preferences: PreferencesRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent { val prefs by preferences.preferences.collectAsStateWithLifecycle(Preferences()); SideEffect { app.tormang.core.designsystem.Strings.language = prefs.language }; TormangTheme(prefs) { TormangNavigation() } }
    }
}
private data class Tab(val route: String, val title: String, val icon: ImageVector)
@Composable fun TormangNavigation() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "library"
    val tabs = listOf(Tab("library", l10n("Библиотека"), Icons.Outlined.CollectionsBookmark), Tab("browse", l10n("Обзор"), Icons.Outlined.Explore), Tab("updates", l10n("Новинки"), Icons.Outlined.NewReleases), Tab("history", l10n("История"), Icons.Outlined.History), Tab("settings", l10n("Ещё"), Icons.Outlined.MoreHoriz))
    val isRoot = tabs.any { it.route == route }
    val isReader = route.startsWith("reader/")
    val tab: (String) -> Unit = { target -> nav.navigate(target) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } }
    val manga: (String) -> Unit = { nav.navigate("manga/${Uri.encode(it)}") }
    val read: (String) -> Unit = { nav.navigate("reader/${Uri.encode(it)}") }
    val back: () -> Unit = { nav.popBackStack(); Unit }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 840.dp && isRoot
        Row {
            if (rail) NavigationRail(Modifier.fillMaxHeight()) { tabs.forEach { t -> NavigationRailItem(route == t.route, { tab(t.route) }, { Icon(t.icon, t.title) }, label = { Text(t.title) }) } }
            Scaffold(modifier = Modifier.weight(1f), contentWindowInsets = if (isReader) WindowInsets(0) else WindowInsets.safeDrawing,
                bottomBar = { if (isRoot && !rail) NavigationBar { tabs.forEach { t -> NavigationBarItem(route == t.route, { tab(t.route) }, { Icon(t.icon, null) }, label = { Text(t.title, maxLines = 1) }) } } }) { padding ->
                NavHost(nav, startDestination = "library", modifier = Modifier.padding(padding)) {
                    composable("library") { LibraryScreen(manga, { tab("browse") }) }
                    composable("browse") { BrowseScreen(manga) }
                    composable("updates") { UpdatesScreen(read) }
                    composable("history") { HistoryScreen(read) }
                    composable("settings") { SettingsScreen({ nav.navigate("downloads") }, { nav.navigate("backup") }) }
                    composable("manga/{mangaId}", arguments = listOf(navArgument("mangaId") { type = NavType.StringType })) { MangaScreen(back, read, { nav.navigate("tracking/${Uri.encode(it)}") }) }
                    composable("reader/{chapterId}", arguments = listOf(navArgument("chapterId") { type = NavType.StringType })) { ReaderScreen(back, { id -> nav.navigate("reader/${Uri.encode(id)}") { popUpTo("reader/{chapterId}") { inclusive = true } } }) }
                    composable("downloads") { Column { TextButton(onClick = back) { Text(l10n("Назад")) }; DownloadsScreen(read) } }
                    composable("backup") { BackupScreen(back) }
                    composable("tracking/{mangaId}", arguments = listOf(navArgument("mangaId") { type = NavType.StringType })) { TrackingScreen(back) }
                }
            }
        }
    }
}
