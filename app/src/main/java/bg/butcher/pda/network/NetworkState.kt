package bg.butcher.pda.network

enum class NetworkStatus {
    IDLE, PENDING, SUCCESS, ERROR
}

data class NetworkState<T>(
    var status: NetworkStatus = NetworkStatus.IDLE,
    var result: T? = null,
    var error: Throwable? = null,
) {
    companion object {
        fun <T> withSuccess(value: T): NetworkState<T> {
            return NetworkState(
                status = NetworkStatus.SUCCESS,
                result = value,
            )
        }

        fun <T> withError(value: Throwable): NetworkState<T> {
            return NetworkState(
                status = NetworkStatus.ERROR,
                error = value,
            )
        }
    }
}