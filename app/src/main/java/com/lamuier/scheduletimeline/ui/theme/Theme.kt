package com.lamuier.scheduletimeline.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.lamuier.scheduletimeline.data.EventType
import com.lamuier.scheduletimeline.data.ScheduleEvent
import com.lamuier.scheduletimeline.data.ThemeMode
import com.lamuier.scheduletimeline.data.TokutenKind
import kotlin.math.abs

// ---- 品牌色 ----
val PurpleTime = Color(0xFF7C5CFC)
val OrangeGap = Color(0xFFF59E0B)
val RedLocation = Color(0xFFEF4444)
val WarningAmber = Color(0xFFF59E0B)

// ---- 分类配色：一个分类对应一组「块底色 / 强调色」 ----
data class CategoryColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
)

private val CategoryPalette = listOf(
    // 紫
    CategoryColors(Color(0xFF7C5CFC), Color(0xFFEDE8FF), Color(0xFF3B2B8C)),
    // 青
    CategoryColors(Color(0xFF06B6D4), Color(0xFFDDF6FA), Color(0xFF0E5C6B)),
    // 粉
    CategoryColors(Color(0xFFEC4899), Color(0xFFFDE7F1), Color(0xFF8C1D53)),
    // 绿
    CategoryColors(Color(0xFF10B981), Color(0xFFDDF5EC), Color(0xFF0B5C42)),
    // 橙
    CategoryColors(Color(0xFFF97316), Color(0xFFFEEBDC), Color(0xFF83400E)),
    // 蓝
    CategoryColors(Color(0xFF3B82F6), Color(0xFFE1EDFE), Color(0xFF1B4A94)),
    // 红
    CategoryColors(Color(0xFFEF4444), Color(0xFFFDE4E4), Color(0xFF8C2222)),
)

/** 常见分类固定配色，其余分类按名称哈希稳定取色。 */
private val FixedCategoryIndex = mapOf(
    "舞台演出" to 0,
    "特典" to 2,
    "物贩" to 4,
    "签售" to 1,
    "抽选" to 5,
)

fun categoryColors(category: String): CategoryColors {
    val key = category.trim()
    if (key.isEmpty()) return CategoryPalette[5]
    val fixed = FixedCategoryIndex[key]
    val index = fixed ?: (abs(key.hashCode()) % CategoryPalette.size)
    return CategoryPalette[index]
}

fun eventTypeColors(type: EventType): CategoryColors = when (type) {
    EventType.PERFORMANCE -> CategoryPalette[0]
    EventType.TOKUTEN -> CategoryPalette[2]
}

fun eventTypeColors(event: ScheduleEvent): CategoryColors {
    val type = EventType.fromStorage(event.eventType)
    return when (type) {
        EventType.PERFORMANCE -> CategoryPalette[0]
        EventType.TOKUTEN -> when (TokutenKind.fromStorage(event.tokutenKind)) {
            TokutenKind.PRE -> CategoryPalette[1]
            TokutenKind.PARALLEL -> CategoryPalette[2]
            TokutenKind.FINAL -> CategoryPalette[5]
            null -> CategoryPalette[2]
        }
    }
}

/** 深色模式下把浅色容器换成低亮度版本，保持强调色不变。 */
fun CategoryColors.adaptTo(dark: Boolean): CategoryColors {
    if (!dark) return this
    return copy(
        container = accent.copy(alpha = 0.22f),
        onContainer = Color(0xFFEDEAF6),
    )
}

val ScheduleShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LightColors = lightColorScheme(
    primary = PurpleTime,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE8FF),
    onPrimaryContainer = Color(0xFF3B2B8C),
    secondary = OrangeGap,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF92400E),
    tertiary = Color(0xFF06B6D4),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDF6FA),
    onTertiaryContainer = Color(0xFF0E5C6B),
    background = Color(0xFFF6F5FA),
    onBackground = Color(0xFF1B1A21),
    surface = Color.White,
    onSurface = Color(0xFF1B1A21),
    surfaceVariant = Color(0xFFEDECF4),
    onSurfaceVariant = Color(0xFF5F5C6B),
    surfaceDim = Color(0xFFDEDDE4),
    surfaceBright = Color(0xFFFAF9FD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F5FA),
    surfaceContainer = Color(0xFFF0EFF6),
    surfaceContainerHigh = Color(0xFFEAE8F1),
    surfaceContainerHighest = Color(0xFFE4E2EB),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFE3E1EC),
    error = RedLocation,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA48FFF),
    onPrimary = Color(0xFF261A66),
    primaryContainer = Color(0xFF3B2B8C),
    onPrimaryContainer = Color(0xFFEDE8FF),
    secondary = OrangeGap,
    onSecondary = Color(0xFF451A03),
    secondaryContainer = Color(0xFF451A03),
    onSecondaryContainer = Color(0xFFFDE68A),
    tertiary = Color(0xFF22D3EE),
    onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF083344),
    onTertiaryContainer = Color(0xFFA5F3FC),
    background = Color(0xFF141318),
    onBackground = Color(0xFFEDEAF6),
    surface = Color(0xFF1E1C24),
    onSurface = Color(0xFFEDEAF6),
    surfaceVariant = Color(0xFF2A2833),
    onSurfaceVariant = Color(0xFFABA8B8),
    surfaceDim = Color(0xFF141318),
    surfaceBright = Color(0xFF3B3842),
    surfaceContainerLowest = Color(0xFF0F0E13),
    surfaceContainerLow = Color(0xFF1A1820),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2931),
    surfaceContainerHighest = Color(0xFF36343C),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF37343F),
    error = Color(0xFFFF7B7B),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFDE8E8),
)

val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun ScheduleTimelineTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = ScheduleShapes,
            typography = MaterialTheme.typography.copy(
                titleLarge = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 22.sp,
                ),
                titleMedium = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                ),
                bodyLarge = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                ),
                bodyMedium = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                ),
                labelLarge = TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                ),
            ),
            content = content,
        )
    }
}
