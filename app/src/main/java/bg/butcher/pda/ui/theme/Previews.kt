package bg.butcher.pda.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** Each preview in the light and the dark theme. */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

/**
 * The app's own colours rather than the wallpaper's, as on the PDA, whose Android 6 has no dynamic colour.
 * On a surface, so text has the theme's colour in the dark previews too.
 */
@Composable
fun PreviewTheme(content: @Composable () -> Unit) {
    AppTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
