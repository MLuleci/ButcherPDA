package bg.butcher.pda.network

sealed interface NetworkState<out T> {
    data object Idle : NetworkState<Nothing>
    data object Loading : NetworkState<Nothing>
    data class Success<T>(val data: T) : NetworkState<T>
    data class Error(val message: String? = null) : NetworkState<Nothing>
}