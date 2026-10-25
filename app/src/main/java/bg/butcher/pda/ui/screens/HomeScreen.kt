package bg.butcher.pda.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.ui.BatchesGraph
import bg.butcher.pda.ui.ManufactureGraph
import bg.butcher.pda.ui.Settings as SettingsRoute
import bg.butcher.pda.ui.theme.AppTheme

data class NavButton(
    val route: Any,
    @StringRes val title: Int,
    val icon: ImageVector,
)

val buttons = listOf(
    NavButton(ManufactureGraph, R.string.select_title, Icons.Filled.Add),
    NavButton(BatchesGraph, R.string.search_title, Icons.Filled.Search),
    NavButton(SettingsRoute, R.string.settings_title, Icons.Filled.Settings),
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onButtonClicked: (route: Any) -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        buttons.forEachIndexed { index, item ->
            val title = stringResource(item.title)
            if (index > 0) {
                Spacer(modifier = Modifier.height(10.dp))
            }
            TextButton(
                onClick = { onButtonClicked(item.route) },
                modifier = Modifier.width(150.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(imageVector = item.icon, contentDescription = title)
                    Text(title)
                }
            }
        }
    }
}

@Preview
@Composable
fun StartScreenPreview() {
    AppTheme {
        HomeScreen()
    }
}