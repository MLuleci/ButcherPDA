package bg.butcher.pda.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import bg.butcher.pda.MainApplication
import bg.butcher.pda.R
import bg.butcher.pda.hardware.ScannedCode
import bg.butcher.pda.hardware.parseScan
import bg.butcher.pda.network.NetworkState
import bg.butcher.pda.ui.components.BatchDialogState
import bg.butcher.pda.ui.components.BatchDialogStateSaver
import bg.butcher.pda.ui.components.BatchInput
import bg.butcher.pda.ui.components.OnScan
import bg.butcher.pda.ui.components.SearchField
import bg.butcher.pda.ui.screens.FormScreen
import bg.butcher.pda.ui.screens.FormUiEffect
import bg.butcher.pda.ui.screens.FormViewModel
import bg.butcher.pda.ui.screens.HomeScreen
import bg.butcher.pda.ui.screens.SettingsScreen
import bg.butcher.pda.ui.screens.StartScreen
import bg.butcher.pda.ui.screens.SummaryScreen
import bg.butcher.pda.ui.screens.canFetch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppBar(
    @StringRes title: Int,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    navigateUpEnabled: Boolean = true,
) {
    TopAppBar(
        title = { Text(stringResource(title)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp, enabled = navigateUpEnabled) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_label)
                    )
                }
            }
        }
    )
}

@Composable
fun MainApp(
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val scanFeedback = (LocalContext.current.applicationContext as MainApplication).container.scanFeedback
    // Set by a route that mustn't be left yet, e.g. Form while submitting
    var backBlocked by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MainAppBar(
                title = backStackEntry?.destination.title(),
                canNavigateBack = navController.previousBackStackEntry != null,
                navigateUpEnabled = !backBlocked,
                // Through the dispatcher, so the routes' BackHandlers apply to it too
                navigateUp = { backDispatcher?.onBackPressed() ?: navController.navigateUp() }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable<Home> {
                HomeScreen(
                    onButtonClicked = { navController.navigate(it) { launchSingleTop = true } },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable<Settings> {
                SettingsScreen(modifier = Modifier.fillMaxSize())
            }

            navigation<ManufactureGraph>(startDestination = Start) {
                composable<Start> {
                    val viewModel = it.formViewModel(navController)
                    val state by viewModel.uiState.collectAsState()
                    CollectFormEffects(viewModel, navController)
                    StartScreen(
                        state = state,
                        onQuantityChanged = viewModel::setQuantity,
                        onExplodeChanged = viewModel::setExplode,
                        onExpiryChanged = viewModel::setDaysUntilExpiry,
                        onFinishedChanged = viewModel::setIsFinished,
                        onQueryChanged = viewModel::searchForBom,
                        onItemSelected = viewModel::setSelectedBom,
                        onContinue = viewModel::fetchBomDetails,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                composable<Form> {
                    val viewModel = it.formViewModel(navController)
                    val state by viewModel.uiState.collectAsState()
                    CollectFormEffects(viewModel, navController)
                    LaunchedEffect(viewModel) {
                        if (state.canFetch) viewModel.reloadBomDetails() else navController.popBackStack()
                    }
                    // Stay on Form until the submit finishes: after Back, the operator could change the run
                    // on Start and the late result would open Summary for it, or leave the graph and never
                    // see an entry the server did create
                    val submitting = state.submitState is NetworkState.Loading
                    BackHandler(enabled = submitting) {}
                    DisposableEffect(submitting) {
                        backBlocked = submitting
                        onDispose { backBlocked = false }
                    }
                    var dialog by rememberSaveable(stateSaver = BatchDialogStateSaver) {
                        mutableStateOf<BatchDialogState?>(null)
                    }
                    val context = LocalContext.current
                    // Only once the BOM has loaded: there are no ingredients to check a batch against before
                    OnScan(
                        enabled = state.fetchState is NetworkState.Success && !submitting,
                        // A toast, so a failed scan neither opens an empty dialog nor clears a batch already found
                        onNoRead = {
                            scanFeedback.failed()
                            Toast.makeText(context, R.string.scan_no_read, Toast.LENGTH_SHORT).show()
                        },
                    ) { raw ->
                        if (dialog is BatchDialogState.Editing) {
                            Toast.makeText(context, R.string.scan_ignored_while_editing, Toast.LENGTH_SHORT).show()
                        } else {
                            val code = parseScan(raw)
                            dialog = BatchDialogState.Adding(
                                BatchInput(
                                    text = (code as? ScannedCode.Batch)?.number ?: raw.trim(),
                                    fromScan = true,
                                    scanId = System.nanoTime(),
                                    unrecognised = code is ScannedCode.Unrecognised,
                                )
                            )
                        }
                    }
                    FormScreen(
                        state = state,
                        dialog = dialog,
                        onDialogChange = { dialog = it },
                        lookupBatch = viewModel::lookupBatch,
                        onAddBatch = viewModel::addBatch,
                        onUpdateBatch = viewModel::updateBatch,
                        onRemoveBatch = { itemCode, batchNumber -> viewModel.removeBatch(itemCode, batchNumber) },
                        onSubmit = viewModel::submit,
                        onRetry = viewModel::reloadBomDetails,
                        onScanAccepted = scanFeedback::accepted,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                composable<Summary> {
                    val viewModel = it.formViewModel(navController)
                    val state by viewModel.uiState.collectAsState()
                    CollectFormEffects(viewModel, navController)
                    val done = { navController.popBackStack<ManufactureGraph>(inclusive = true) }
                    BackHandler { done() }
                    // Kept while the page animates out after "Same BOM again" clears it
                    val entry = remember { (state.submitState as? NetworkState.Success)?.data }
                    if (entry != null) {
                        SummaryScreen(
                            entry = entry,
                            printState = state.printState,
                            onPrint = viewModel::printLabel,
                            onSameBomAgain = {
                                viewModel.startAgain()
                                navController.popBackStack<Start>(inclusive = false)
                            },
                            onDone = { done() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Placeholders until the real Search and Details screens (Steps 6.3 and 6.4)
            navigation<BatchesGraph>(startDestination = Search) {
                composable<Search> {
                    val open = { batch: String -> navController.navigate(Details(batch)) { launchSingleTop = true } }
                    OnScan { raw -> (parseScan(raw) as? ScannedCode.Batch)?.let { open(it.number) } }
                    val query = rememberTextFieldState()
                    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                        SearchField(query, requestFocus = true)
                        Button(onClick = { query.text.toString().trim().takeIf { it.isNotEmpty() }?.let(open) }) {
                            Text(stringResource(R.string.search_label))
                        }
                    }
                }
                composable<Details> { entry ->
                    val details = entry.toRoute<Details>()
                    // Replaces this page, so Back always goes to Search
                    OnScan { raw ->
                        (parseScan(raw) as? ScannedCode.Batch)?.let {
                            navController.navigate(Details(it.number)) { popUpTo<Details> { inclusive = true } }
                        }
                    }
                    Text(details.batch, modifier = Modifier.padding(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CollectFormEffects(viewModel: FormViewModel, navController: NavController) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(viewModel, navController) {
        viewModel.effects.collect { effect ->
            when (effect) {
                FormUiEffect.GotoForm -> {
                    navController.navigate(Form) { launchSingleTop = true }
                }
                // Replaces Form, so Back can't reach the submitted form
                FormUiEffect.GotoSummary -> {
                    navController.navigate(Summary) {
                        popUpTo<Form> { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is FormUiEffect.ShowToast -> {
                    Toast.makeText(context, resources.getString(effect.messageResId), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun NavBackStackEntry.formViewModel(navController: NavController): FormViewModel {
    val graphEntry = remember(this) { navController.getBackStackEntry<ManufactureGraph>() }
    return viewModel(viewModelStoreOwner = graphEntry, factory = FormViewModel.Factory)
}