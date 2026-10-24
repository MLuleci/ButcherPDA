package bg.butcher.pda.ui

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import bg.butcher.pda.R
import bg.butcher.pda.ui.screens.FormScreen
import bg.butcher.pda.ui.screens.FormViewModel
import bg.butcher.pda.ui.screens.FormUiEffect
import bg.butcher.pda.ui.screens.HomeScreen
import bg.butcher.pda.ui.screens.StartScreen
import bg.butcher.pda.ui.screens.SettingsScreen

// Nested graph for the manufacturing flow; its screens share one FormViewModel
const val MANUFACTURE_GRAPH = "manufacture"

enum class MainScreen(@StringRes val title: Int) {
    Home(title = R.string.app_name),
    Settings(title = R.string.settings_title),

    // Manufacturing flow:
    Start(title = R.string.select_title),
    Form(title = R.string.assembly_title),
    Summary(title = R.string.summary_title),

    // Search flow:
    Search(title = R.string.search_title),
    Details(title = R.string.details_title),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppBar(
    currentScreen: MainScreen,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(stringResource(currentScreen.title)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
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
    val currentScreen = MainScreen.entries.firstOrNull { it.name == backStackEntry?.destination?.route }
        ?: MainScreen.Home

    Scaffold(
        topBar = {
            MainAppBar(
                currentScreen = currentScreen,
                canNavigateBack = navController.previousBackStackEntry != null,
                navigateUp = { navController.navigateUp() }
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainScreen.Home.name,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(route = MainScreen.Home.name) {
                HomeScreen(
                    onButtonClicked = { navController.navigate(it) { launchSingleTop = true } },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable(route = MainScreen.Settings.name) {
                SettingsScreen(modifier = Modifier.fillMaxSize())
            }

            navigation(route = MANUFACTURE_GRAPH, startDestination = MainScreen.Start.name) {
                composable(route = MainScreen.Start.name) {
                    val viewModel = it.formViewModel(navController)
                    val state by viewModel.uiState.collectAsState()
                    val context = LocalContext.current
                    val resources = LocalResources.current

                    LaunchedEffect(viewModel, navController) {
                        viewModel.effects.collect { effect ->
                            when (effect) {
                                FormUiEffect.GotoNextPage -> {
                                    navController.navigate(MainScreen.Form.name) { launchSingleTop = true }
                                }
                                is FormUiEffect.ShowToast -> {
                                    Toast.makeText(
                                        context,
                                        resources.getString(effect.messageResId),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    }

                    StartScreen(
                        state = state,
                        onQuantityChanged = viewModel::setQuantity,
                        onExplodeChanged = viewModel::setExplode,
                        onQueryChanged = viewModel::searchForBom,
                        onItemSelected = viewModel::setSelectedBom,
                        onContinue = viewModel::fetchBomDetails,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                composable(route = MainScreen.Form.name) {
                    val viewModel = it.formViewModel(navController)
                    val state by viewModel.uiState.collectAsState()
                    LaunchedEffect(viewModel) {
                        if (state.canContinue) viewModel.reloadBomDetails() else navController.popBackStack()
                    }
                    FormScreen(
                        state = state,
                        onExpiryChanged = viewModel::setDaysUntilExpiry,
                        onFinishedChanged = viewModel::setIsFinished,
                        onSubmit = {},
                        onRetry = viewModel::reloadBomDetails,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBackStackEntry.formViewModel(navController: NavController): FormViewModel {
    val graphEntry = remember(this) { navController.getBackStackEntry(MANUFACTURE_GRAPH) }
    return viewModel(viewModelStoreOwner = graphEntry, factory = FormViewModel.Factory)
}