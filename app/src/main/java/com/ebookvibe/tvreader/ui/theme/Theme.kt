package com.ebookvibe.tvreader.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/** Overscan-safe margin: no UI is placed closer than this to the screen edge. */
val SafeMargin = 48.dp

/** High-contrast focus colours: amber fill + white ring reads well even on a dim projector image. */
val FocusFill = Color(0xFFFFC107)
val FocusRing = Color.White
val SurfaceIdle = Color(0xFF262A33)
val Background = Color(0xFF0E1015)

private val Colors = darkColorScheme(
    primary = FocusFill,
    onPrimary = Color.Black,
    background = Background,
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF181B22),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = SurfaceIdle,
    onSurfaceVariant = Color(0xFFC9CED8),
    border = Color(0xFF5A6070),
)

// Nothing below 18sp: text has to be legible from the sofa on a projected image.
private val Type = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 21.sp),
    bodyMedium = TextStyle(fontSize = 19.sp),
    bodySmall = TextStyle(fontSize = 18.sp),
    labelLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 18.sp),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
