package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val StudioColorScheme = darkColorScheme(
    primary = CyanSignal,
    onPrimary = StudioObsidian,
    primaryContainer = CyanContainer,
    onPrimaryContainer = CyanSignal,
    secondary = RecordCoral,
    onSecondary = TextPrimary,
    secondaryContainer = RecordCoralContainer,
    onSecondaryContainer = RecordCoral,
    tertiary = EmeraldSafe,
    onTertiary = StudioObsidian,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldSafe,
    background = StudioObsidian,
    onBackground = TextPrimary,
    surface = StudioRack,
    onSurface = TextPrimary,
    surfaceVariant = StudioPanel,
    onSurfaceVariant = TextSecondary,
    surfaceContainerHigh = StudioPanelElevated,
    outline = StudioBorder,
    outlineVariant = StudioBorder,
    error = RecordCoral,
    onError = TextPrimary,
    errorContainer = RecordCoralContainer,
    onErrorContainer = RecordCoral
)

private val StudioShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun VoxForgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
