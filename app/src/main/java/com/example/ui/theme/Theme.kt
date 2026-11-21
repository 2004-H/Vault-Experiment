package com.example.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val VaultDarkColorScheme = darkColorScheme(
    primary = AmethystLight,
    onPrimary = Color(0xFF140D33),
    primaryContainer = AmethystContainerDark,
    onPrimaryContainer = Color(0xFFE7E0FF),
    secondary = TealLight,
    onSecondary = Color(0xFF062926),
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary = AmberLight,
    onTertiary = Color(0xFF2B1B04),
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = Color(0xFFFDE68A),
    background = ObsidianBg,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianTextSecondary,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceVariant,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    error = RosePriority,
    onError = Color.White,
    errorContainer = RoseContainerDark,
    onErrorContainer = Color(0xFFFFD9E0)
)

private val VaultLightColorScheme = lightColorScheme(
    primary = AmethystPrimary,
    onPrimary = Color.White,
    primaryContainer = AmethystContainerLight,
    onPrimaryContainer = Color(0xFF231163),
    secondary = TealQuery,
    onSecondary = Color.White,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = Color(0xFF0B423D),
    tertiary = AmberProperty,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = Color(0xFF452B03),
    background = PaperBg,
    onBackground = PaperTextPrimary,
    surface = PaperSurface,
    onSurface = PaperTextPrimary,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = PaperTextSecondary,
    surfaceContainerLow = PaperSurface,
    surfaceContainer = PaperSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE5E7F1),
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    error = RosePriority,
    onError = Color.White,
    errorContainer = RoseContainerLight,
    onErrorContainer = Color(0xFF5A0A1B)
)

val VaultShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun VaultBlockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> VaultDarkColorScheme
        else -> VaultLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = VaultTypography,
        shapes = VaultShapes,
        content = content
    )
}
