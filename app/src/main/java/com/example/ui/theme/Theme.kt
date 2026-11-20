package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val VocaStageDarkColorScheme = darkColorScheme(
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = CoralContainer,
    onPrimaryContainer = OnCoralContainer,
    secondary = AmberSecondary,
    onSecondary = Color(0xFF1D1202),
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = MintTertiary,
    onTertiary = Color(0xFF042016),
    tertiaryContainer = MintContainer,
    onTertiaryContainer = OnMintContainer,
    background = StudioBg,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = StudioBg,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighest,
    surfaceContainerHighest = Color(0xFF2E344F),
    outline = StudioOutline,
    outlineVariant = StudioOutlineVariant,
    error = ErrorRed,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFFFFDAD6)
)

val VocaStageShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VocaStageTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VocaStageDarkColorScheme,
        typography = Typography,
        shapes = VocaStageShapes,
        content = content
    )
}
