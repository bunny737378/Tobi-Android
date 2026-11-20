package com.tobi.app.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class TobiPalette(
    val canvas: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val ctaFill: Color,
    val ctaInk: Color,
    val accent: Color,
    val warning: Color,
    val success: Color,
    val navBar: Color
)

val LocalTobiPalette = staticCompositionLocalOf {
    TobiPalette(
        canvas = Color(0xFF0D0F12),
        surface1 = Color(0xFF15181E),
        surface2 = Color(0xFF1D2129),
        surface3 = Color(0xFF262B36),
        hairline = Color.White.copy(alpha = 0.07f),
        textPrimary = Color(0xFFECEFF4),
        textSecondary = Color(0xFF9AA2B1),
        textMuted = Color(0xFF6B7280),
        ctaFill = Color(0xFFF0F2F6),
        ctaInk = Color(0xFF0D0F12),
        accent = Color(0xFFE04F5F),
        warning = Color(0xFFD99B4E),
        success = Color(0xFF4EA87E),
        navBar = Color(0xFF12151B)
    )
}

val AccentPresets: List<Pair<String, Color>> = listOf(
    "Crimson" to Color(0xFFE04F5F),
    "Slate Ice" to Color(0xFF5B9BD5),
    "Warm Amber" to Color(0xFFD99B4E),
    "Sage" to Color(0xFF4EA87E)
)

fun accentColorFromHex(hex: String): Color {
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6) return Color(0xFFE04F5F)
    val value = cleaned.toLongOrNull(16) ?: return Color(0xFFE04F5F)
    return Color(0xFF000000 or value)
}

fun colorToHex(color: Color): String {
    val red = (color.red * 255).toInt()
    val green = (color.green * 255).toInt()
    val blue = (color.blue * 255).toInt()
    return String.format("#%02X%02X%02X", red, green, blue)
}

val TobiTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    )
)

@Composable
fun TobiTheme(
    oled: Boolean,
    accentHex: String,
    content: @Composable () -> Unit
) {
    val accent = accentColorFromHex(accentHex)
    val canvas = if (oled) Color(0xFF000000) else Color(0xFF0D0F12)
    val palette = TobiPalette(
        canvas = canvas,
        surface1 = Color(0xFF15181E),
        surface2 = Color(0xFF1D2129),
        surface3 = Color(0xFF262B36),
        hairline = Color.White.copy(alpha = 0.07f),
        textPrimary = Color(0xFFECEFF4),
        textSecondary = Color(0xFF9AA2B1),
        textMuted = Color(0xFF6B7280),
        ctaFill = Color(0xFFF0F2F6),
        ctaInk = Color(0xFF0D0F12),
        accent = accent,
        warning = Color(0xFFD99B4E),
        success = Color(0xFF4EA87E),
        navBar = Color(0xFF12151B)
    )
    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color(0xFF0D0F12),
        secondary = palette.textSecondary,
        background = canvas,
        onBackground = palette.textPrimary,
        surface = palette.surface1,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surface2,
        onSurfaceVariant = palette.textSecondary,
        surfaceContainer = palette.surface2,
        surfaceContainerHigh = palette.surface3,
        outline = palette.hairline,
        error = accent,
        onError = Color(0xFF0D0F12)
    )
    CompositionLocalProvider(LocalTobiPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TobiTypography,
            content = content
        )
    }
}

fun formatDurationMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

fun formatTimecode(ms: Long): String {
    if (ms <= 0) return "00:00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.size - 1) {
        value /= 1024
        index++
    }
    return if (index == 0) "$bytes B" else String.format("%.1f %s", value, units[index])
}

fun formatRemaining(ms: Long): String {
    if (ms <= 0) return ""
    val totalMinutes = ms / 60000
    return if (totalMinutes >= 60) {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        if (minutes == 0L) "${hours}h left" else "${hours}h ${minutes}m left"
    } else {
        "${totalMinutes}m left"
    }
}
