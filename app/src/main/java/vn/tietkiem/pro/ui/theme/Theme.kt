package vn.tietkiem.pro.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C4C),
    secondary = Color(0xFF4D6358),
    tertiary = Color(0xFF3D6374)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF56DBA5),
    secondary = Color(0xFFB4CCBE),
    tertiary = Color(0xFFA5CDDF)
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
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) DarkColors else LightColors

    (context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !dark }
    MaterialTheme(colorScheme = colors, content = content)
}
