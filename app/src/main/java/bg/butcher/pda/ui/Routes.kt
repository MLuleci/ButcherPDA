package bg.butcher.pda.ui

import androidx.annotation.StringRes
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import bg.butcher.pda.R
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object Home
@Serializable data object Settings

// Its screens share one FormViewModel
@Serializable data object ManufactureGraph
@Serializable data object Start
@Serializable data object Form
@Serializable data object Summary

@Serializable data object BatchesGraph
@Serializable data object Search
@Serializable data class Details(val batch: String)

private val TITLES: List<Pair<KClass<out Any>, Int>> = listOf(
    Home::class to R.string.app_name,
    Settings::class to R.string.settings_title,
    Start::class to R.string.select_title,
    Form::class to R.string.assembly_title,
    Summary::class to R.string.summary_title,
    Search::class to R.string.search_title,
    Details::class to R.string.details_title,
)

@StringRes
fun NavDestination?.title(): Int =
    TITLES.firstOrNull { (route, _) -> this?.hasRoute(route) == true }?.second ?: R.string.app_name
