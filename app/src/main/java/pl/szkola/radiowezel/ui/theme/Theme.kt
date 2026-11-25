package pl.szkola.radiowezel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val PanelBg = Color(0xFF14161C)
val PanelRaised = Color(0xFF1C1F28)
val PanelLine = Color(0xFF2E3340)
val Ink = Color(0xFFE8E6E1)
val InkDim = Color(0xFF9A9AA3)
val Amber = Color(0xFFFFB020)
val Signal = Color(0xFF3CB371)
val Warn = Color(0xFFE85D4C)

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1200),
    secondary = Signal,
    onSecondary = Color(0xFF04140A),
    background = PanelBg,
    onBackground = Ink,
    surface = PanelRaised,
    onSurface = Ink,
    surfaceVariant = Color(0xFF252934),
    onSurfaceVariant = InkDim,
    outline = PanelLine,
    error = Warn,
    onError = Color.White
)

private val Type = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        letterSpacing = (-1).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = InkDim
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp
    )
)

@Composable
fun RadiowezelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Type,
        content = content
    )
}
