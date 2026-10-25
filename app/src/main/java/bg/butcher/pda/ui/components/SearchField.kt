package bg.butcher.pda.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import bg.butcher.pda.R
import bg.butcher.pda.ui.theme.PreviewTheme
import bg.butcher.pda.ui.theme.ThemePreviews

@Composable
fun SearchField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    placeholder: String = stringResource(R.string.search_label),
    // Enter, before the keyboard is put away
    onSearch: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    TextField(
        state = state,
        lineLimits = TextFieldLineLimits.SingleLine,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_label)) },
        trailingIcon = {
            if (state.text.isNotEmpty()) {
                IconButton(onClick = { state.clearText() }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_label))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // Results update as you type, so Enter doesn't need to start a search
        onKeyboardAction = {
            onSearch()
            focusManager.clearFocus()
        },
        modifier = modifier
            .fillMaxWidth()
            // Not redundant: without it, clearFocus() on picking a BOM leaves the field focused
            .focusable()
            .focusRequester(focusRequester)
    )
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
        }
    }
}

@ThemePreviews
@Composable
private fun SearchField_Empty_Preview() {
    PreviewTheme {
        SearchField(rememberTextFieldState())
    }
}

@ThemePreviews
@Composable
private fun SearchField_Text_Preview() {
    PreviewTheme {
        SearchField(rememberTextFieldState("saus"))
    }
}
