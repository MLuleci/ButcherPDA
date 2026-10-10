package bg.butcher.pda.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import bg.butcher.pda.R
import bg.butcher.pda.model.BomDetails
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.network.NetworkStatus
import bg.butcher.pda.ui.components.ErrorMessage
import bg.butcher.pda.ui.components.RowWithLabel

@Composable
fun AssemblyScreen(
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssemblyViewModel = viewModel(factory = AssemblyViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val fetch = {
        viewModel.fetchBomDetails(
            name = uiState.bomSelected?.name.orEmpty(),
            quantity = uiState.quantity,
            explode = uiState.explode,
        )
    }

    LaunchedEffect(Unit) { fetch() }
    Box(contentAlignment = Alignment.Center) {
        when (uiState.fetchState.status) {
            NetworkStatus.IDLE -> Unit
            NetworkStatus.ERROR -> ErrorMessage(fetch)
            NetworkStatus.PENDING -> CircularProgressIndicator()
            NetworkStatus.SUCCESS -> {
                uiState.fetchState.result?.let {
                    Column(modifier = Modifier.fillMaxSize()) {
                        RowWithLabel(
                            label = R.string.bom_label,
                            item = { Text(it.name) }
                        )
                        RowWithLabel(
                            label = R.string.quantity_label,
                            item = { Text(it.quantity.toString()) }
                        )
                        RowWithLabel(
                            label = R.string.expiry_label,
                            item = {  }
                        )
                    }
                }
            }
        }
    }
}