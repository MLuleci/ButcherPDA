package bg.butcher.pda.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import bg.butcher.pda.R
import bg.butcher.pda.ui.screens.AssemblyScreen
import bg.butcher.pda.ui.screens.AssemblyViewModel
import bg.butcher.pda.ui.screens.SelectScreen
import bg.butcher.pda.ui.screens.SettingsScreen
import bg.butcher.pda.ui.screens.StartScreen

enum class MainScreen(@StringRes val title: Int) {
    Start(title = R.string.app_name),
    Settings(title = R.string.settings_title),

    // Manufacturing flow:
    Select(title = R.string.select_title),
    Assembly(title = R.string.assembly_title),
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
    val currentScreen = MainScreen.valueOf(
        backStackEntry?.destination?.route ?: MainScreen.Start.name
    )

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
            startDestination = MainScreen.Start.name,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
        ) {
            composable(route = MainScreen.Start.name) {
                StartScreen(
                    onButtonClicked = { navController.navigate(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable(route = MainScreen.Settings.name) {
                SettingsScreen(modifier = Modifier.fillMaxSize())
            }

            composable(route = MainScreen.Select.name) {
                SelectScreen(
                    onNavigate = { navController.navigate(MainScreen.Assembly.name) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            composable(route = MainScreen.Assembly.name) {
                val parentEntry = remember(it) { navController.getBackStackEntry(MainScreen.Select.name) }
                val parentViewModel: AssemblyViewModel = viewModel(parentEntry)
                AssemblyScreen(
                    viewModel = parentViewModel,
                    onSubmit = {}
                )
            }
        }
    }
}