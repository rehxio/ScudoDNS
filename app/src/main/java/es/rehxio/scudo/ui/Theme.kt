package es.rehxio.scudo.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Paleta propia (verde azulado) para que la app tenga identidad y se lea igual en todos los móviles. */
private val Light = lightColorScheme(
    primary = Color(0xFF0F6E56), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE2), onPrimaryContainer = Color(0xFF04342C),
    secondary = Color(0xFF3F6158), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE1F5EE), onSecondaryContainer = Color(0xFF085041),
    background = Color(0xFFF6F8F7), onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF6F8F7), onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDDE5E1), onSurfaceVariant = Color(0xFF55605C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F4F2),
    surfaceContainer = Color(0xFFEAF0ED), surfaceContainerHigh = Color(0xFFE4EBE8),
    surfaceContainerHighest = Color(0xFFDEE6E2),
    outline = Color(0xFF8A9590), outlineVariant = Color(0xFFD3DCD8),
    error = Color(0xFFA32D2D), onError = Color.White,
    errorContainer = Color(0xFFFCEBEB), onErrorContainer = Color(0xFF501313),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5DCAA5), onPrimary = Color(0xFF04342C),
    primaryContainer = Color(0xFF085041), onPrimaryContainer = Color(0xFFE1F5EE),
    secondary = Color(0xFFA9CEC2), onSecondary = Color(0xFF12352C),
    secondaryContainer = Color(0xFF213F37), onSecondaryContainer = Color(0xFFC7EADD),
    background = Color(0xFF0E1412), onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0E1412), onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBDC9C4),
    surfaceContainerLowest = Color(0xFF090F0D), surfaceContainerLow = Color(0xFF151C1A),
    surfaceContainer = Color(0xFF19201E), surfaceContainerHigh = Color(0xFF232B28),
    surfaceContainerHighest = Color(0xFF2E3633),
    outline = Color(0xFF87938E), outlineVariant = Color(0xFF3F4945),
    error = Color(0xFFF09595), onError = Color(0xFF501313),
    errorContainer = Color(0xFF791F1F), onErrorContainer = Color(0xFFFCEBEB),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ScudoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        shapes = AppShapes,
        content = content,
    )
}

/** Colores de estado del registro: rojo para bloqueadas, verde para permitidas. */
object StatusColors {
    val blocked: Color @Composable get() = MaterialTheme.colorScheme.error
    val blockedBg: Color @Composable get() = MaterialTheme.colorScheme.errorContainer
    val allowed: Color @Composable get() = MaterialTheme.colorScheme.primary
    val allowedBg: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
    val failed: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFFEF9F27) else Color(0xFF854F0B)
    val failedBg: Color @Composable get() = if (isSystemInDarkTheme()) Color(0xFF633806) else Color(0xFFFAEEDA)
}
