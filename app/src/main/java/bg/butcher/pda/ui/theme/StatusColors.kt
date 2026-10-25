package bg.butcher.pda.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours for states the Material scheme has no role for, next to its `error`: something to check (warning),
 * and something that's right (success). The same in the dynamic colour schemes, so their meaning stays put.
 */
@Immutable
data class StatusColors(
    val warning: ColorFamily,
    val success: ColorFamily,
)

private val lightStatusColors = StatusColors(
    warning = ColorFamily(
        color = Color(0xFF8A5100),
        onColor = Color(0xFFFFFFFF),
        colorContainer = Color(0xFFFFDCBE),
        onColorContainer = Color(0xFF693C00),
    ),
    success = ColorFamily(
        color = Color(0xFF1B6D2F),
        onColor = Color(0xFFFFFFFF),
        colorContainer = Color(0xFFA3F5AE),
        onColorContainer = Color(0xFF005320),
    ),
)

private val darkStatusColors = StatusColors(
    warning = ColorFamily(
        color = Color(0xFFFFB951),
        onColor = Color(0xFF492900),
        colorContainer = Color(0xFF693C00),
        onColorContainer = Color(0xFFFFDCBE),
    ),
    success = ColorFamily(
        color = Color(0xFF7DDB8A),
        onColor = Color(0xFF003914),
        colorContainer = Color(0xFF005320),
        onColorContainer = Color(0xFFA3F5AE),
    ),
)

fun statusColors(darkTheme: Boolean): StatusColors = if (darkTheme) darkStatusColors else lightStatusColors

val LocalStatusColors = staticCompositionLocalOf { lightStatusColors }

/** The status colours of the current theme, alongside `MaterialTheme.colorScheme`. */
val statusColors: StatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStatusColors.current
