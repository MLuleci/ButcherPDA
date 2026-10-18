package bg.butcher.pda.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.components.Centered
import bg.butcher.pda.ui.components.ErrorMessage
import bg.butcher.pda.ui.components.ExpiryInput
import bg.butcher.pda.ui.components.IngredientTable

@Composable
fun FormScreen(
    state: FormUiState,
    onExpiryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        when (state.fetchState) {
            is NetworkState.Error -> Centered { ErrorMessage() }
            is NetworkState.Loading -> Centered { CircularProgressIndicator() }
            is NetworkState.Success -> {
                val bom = state.fetchState.data
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize().padding(20.dp)
                ) {
                    Text(stringResource(R.string.bom_label) + ": ${bom.itemName} (${bom.name})")
                    Text(stringResource(R.string.quantity_label) + ": ${bom.quantity} ${bom.uom}")
                    ExpiryInput(
                        value = state.expiryDaysText,
                        onValueChange = onExpiryChanged,
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        IngredientTable(
                            ingredients = bom.items,
                            batches = state.items,
                        )
                    }
                    IconButton(onClick = { showDialog = true }) {
                        Icon(Icons.Default.AddCircle, contentDescription = "Add")
                    }
                }
            }
            else -> {}
        }
    }
}