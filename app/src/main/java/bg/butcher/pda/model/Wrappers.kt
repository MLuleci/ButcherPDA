package bg.butcher.pda.model

import kotlinx.serialization.Serializable

/* Frappe wraps the return value of `/api/method/...` calls in `message`,
 * and the documents from `/api/resource/...` calls in `data`.
 */

@Serializable
data class MessageWrapper<T>(val message: T)

@Serializable
data class DataWrapper<T>(val data: T)
