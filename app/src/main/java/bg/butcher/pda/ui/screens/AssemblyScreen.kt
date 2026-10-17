package bg.butcher.pda.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import bg.butcher.pda.R
import bg.butcher.pda.network.NetworkStatus
import bg.butcher.pda.ui.components.Centered
import bg.butcher.pda.ui.components.ErrorMessage
import bg.butcher.pda.ui.components.ExpiryInput
import bg.butcher.pda.ui.components.IngredientTable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import bg.butcher.pda.ui.components.BatchDialog

@Composable
fun AssemblyScreen(
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssemblyViewModel = viewModel(factory = AssemblyViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val fetchDetails = {
        viewModel.fetchBomDetails(
            name = uiState.bomSelected?.name.orEmpty(),
            quantity = uiState.quantity,
            explode = uiState.explode,
        )
    }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { fetchDetails() }
    Box(modifier = modifier) {
        when (uiState.bomFetchState.status) {
            NetworkStatus.IDLE -> {}
            NetworkStatus.ERROR -> Centered { ErrorMessage(onRetry = fetchDetails) }
            NetworkStatus.PENDING -> Centered { CircularProgressIndicator() }
            NetworkStatus.SUCCESS -> {
                val result = uiState.bomFetchState.result
                if (result == null) {
                    Centered { ErrorMessage() }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().padding(20.dp)
                    ) {
                        Text(stringResource(R.string.bom_label) + ": ${result.itemName} (${result.name})")
                        Text(stringResource(R.string.quantity_label) + ": ${result.quantity} ${result.uom}")
                        ExpiryInput(
                            onValueChanged = { viewModel.setDaysUntilExpiry(it) },
                            initialValue = result.shelfLife,
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            IngredientTable(
                                items = result.items,
                                batches = uiState.items,
                            )
                        }
                        IconButton(onClick = { showDialog = true }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add")
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        BatchDialog(
            onDismiss = { showDialog = false },
            viewModel = viewModel,
        )
    }
}