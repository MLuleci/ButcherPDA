package bg.butcher.pda.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import bg.butcher.pda.R
import bg.butcher.pda.model.BatchFilters
import bg.butcher.pda.model.BatchSort
import kotlinx.serialization.json.Json

private val BatchFiltersSaver = Saver<BatchFilters, String>(
    save = { Json.encodeToString(it) },
    restore = { runCatching { Json.decodeFromString<BatchFilters>(it) }.getOrDefault(BatchFilters()) },
)

@StringRes
private fun BatchSort.label(): Int = when (this) {
    BatchSort.PRODUCED -> R.string.sort_produced
    BatchSort.EXPIRES -> R.string.sort_expires
    BatchSort.PRODUCT -> R.string.sort_product
}

/** The labels follow the sort: "Newest / Oldest first", "Soonest / Latest first", "A → Z / Z → A". */
@StringRes
fun BatchSort.orderLabel(descending: Boolean): Int = when (this) {
    BatchSort.PRODUCED -> if (descending) R.string.order_newest_first else R.string.order_oldest_first
    BatchSort.EXPIRES -> if (descending) R.string.order_latest_first else R.string.order_soonest_first
    BatchSort.PRODUCT -> if (descending) R.string.order_z_to_a else R.string.order_a_to_z
}

/** Edits a copy of [filters] and hands it to [onClose] when the sheet closes, however it's closed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchFilterSheet(
    filters: BatchFilters,
    onClose: (BatchFilters) -> Unit,
) {
    var draft by rememberSaveable(stateSaver = BatchFiltersSaver) { mutableStateOf(filters) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // A day limit that isn't a whole number keeps the previous one
    val daysError = draft.producedWithinDaysText.isNotBlank() && draft.producedWithinDays == null
    val result = if (daysError) draft.copy(producedWithinDaysText = filters.producedWithinDaysText) else draft

    ModalBottomSheet(onDismissRequest = { onClose(result) }, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(stringResource(R.string.filters_label), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.sort_by_label), style = MaterialTheme.typography.titleSmall)
            BatchSort.entries.forEach { sort ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = draft.sort == sort,
                            role = Role.RadioButton,
                            onClick = { draft = draft.withSort(sort) },
                        )
                ) {
                    RadioButton(selected = draft.sort == sort, onClick = null)
                    Text(stringResource(sort.label()), modifier = Modifier.padding(start = 12.dp))
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(false, true).forEachIndexed { index, descending ->
                    SegmentedButton(
                        selected = draft.descending == descending,
                        onClick = { draft = draft.copy(descending = descending) },
                        shape = SegmentedButtonDefaults.itemShape(index, count = 2),
                    ) {
                        Text(stringResource(draft.sort.orderLabel(descending)))
                    }
                }
            }

            OutlinedTextField(
                value = draft.producedWithinDaysText,
                onValueChange = { draft = draft.copy(producedWithinDaysText = it) },
                label = { Text(stringResource(R.string.produced_within_label)) },
                suffix = { Text(stringResource(R.string.days)) },
                singleLine = true,
                isError = daysError,
                supportingText = { if (daysError) Text(stringResource(R.string.whole_number_required)) },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            CheckboxRow(
                label = R.string.hide_expired_label,
                checked = draft.hideExpired,
                onCheckedChange = { draft = draft.copy(hideExpired = it) },
            )

            ConfirmCancelButtons(
                confirmLabel = stringResource(R.string.done_label),
                cancelLabel = stringResource(R.string.reset_label),
                onConfirm = { onClose(result) },
                onCancel = { draft = BatchFilters() },
            )
        }
    }
}
