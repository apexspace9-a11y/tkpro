package vn.tietkiem.pro.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF335CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E7FF),
    onPrimaryContainer = Color(0xFF0B1D66),
    secondary = Color(0xFF137D72),
    secondaryContainer = Color(0xFFCDEDE8),
    tertiary = Color(0xFF7357A8),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EBF2),
    onSurface = Color(0xFF191B22),
    onSurfaceVariant = Color(0xFF626773),
    outline = Color(0xFFC5C9D3),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF002EAE),
    primaryContainer = Color(0xFF173ECA),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFF94D5CC),
    secondaryContainer = Color(0xFF005049),
    tertiary = Color(0xFFD7BAFF),
    background = Color(0xFF0E1117),
    surface = Color(0xFF171A21),
    surfaceVariant = Color(0xFF252932),
    onSurface = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFFBFC3CE),
    outline = Color(0xFF858A96),
    error = Color(0xFFFFB4AB)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 31.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
)

@Composable
fun TietKiemProTheme(themeMode: String, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }
    val context = LocalContext.current
    val colors = if (dark) DarkColors else LightColors
    (context as? Activity)?.window?.let {
        WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !dark
        WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightNavigationBars = !dark
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
