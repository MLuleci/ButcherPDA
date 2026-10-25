package bg.butcher.pda.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.ui.theme.PreviewTheme
import bg.butcher.pda.ui.theme.ThemePreviews

/** A checkbox with its label, where the whole row toggles it. */
@Composable
fun CheckboxRow(
    @StringRes label: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
    }
}

@ThemePreviews
@Composable
private fun CheckboxRow_Preview() {
    PreviewTheme {
        Column {
            CheckboxRow(label = R.string.explode_label, checked = true, onCheckedChange = {})
            CheckboxRow(label = R.string.finished_label, checked = false, onCheckedChange = {})
        }
    }
}
