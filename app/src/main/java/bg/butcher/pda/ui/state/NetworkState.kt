package bg.butcher.pda.ui.state

sealed interface NetworkState<out T, out E> {
    data object Idle : NetworkState<Nothing, Nothing>
    data object Loading : NetworkState<Nothing, Nothing>
    data class Success<T>(val data: T) : NetworkState<T, Nothing>
    data class Error<E>(val error: E) : NetworkState<Nothing, E>
}