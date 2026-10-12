package app.tormang.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tormang.domain.model.*

object Space { val xs = 4.dp; val sm = 8.dp; val md = 16.dp; val lg = 24.dp; val xl = 32.dp }
object Elevation { val flat = 0.dp; val card = 1.dp; val floating = 6.dp }
private val Light = lightColorScheme(primary = Color(0xFF306957), onPrimary = Color.White, primaryContainer = Color(0xFFC5EDD9), secondary = Color(0xFF74634D), background = Color(0xFFFAF9F5), surface = Color(0xFFFAF9F5), surfaceContainer = Color(0xFFF0F0EA))
private val Dark = darkColorScheme(primary = Color(0xFFA0D4B9), onPrimary = Color(0xFF10382B), primaryContainer = Color(0xFF284E3F), secondary = Color(0xFFD8C3A5), background = Color(0xFF121713), surface = Color(0xFF121713), surfaceContainer = Color(0xFF1D251F))
@Composable fun TormangTheme(preferences: Preferences = Preferences(), content: @Composable () -> Unit) {
    val dark = when (preferences.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; else -> true }
    val colors = if (preferences.dynamicColors && Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current) } else if (dark) Dark else Light
    MaterialTheme(colorScheme = if (preferences.theme == ThemeMode.AMOLED) colors.copy(background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF101010)) else colors,
        typography = Typography(headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium), titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)),
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)), content = content)
}
@Composable fun PageHeading(title: String, subtitle: String, action: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineLarge); if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        action()
    }
}
