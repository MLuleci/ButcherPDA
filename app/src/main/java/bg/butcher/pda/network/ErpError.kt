package bg.butcher.pda.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException

sealed class ErpError(message: String? = null, cause: Throwable? = null) : Exception(message, cause) {
    /** The request may not have reached the server. */
    class Network(cause: IOException) : ErpError(cause.message, cause)

    /** The server answered, but the body isn't what the app expects. */
    class UnexpectedResponse(cause: Throwable) : ErpError(cause.message, cause)

    /** The base URL or API key in Settings is missing or invalid; nothing was sent. */
    class NotConfigured : ErpError()

    /** 401: missing or wrong API key. */
    class Unauthorized : ErpError()

    /** 403: the PDA user's role doesn't allow this. */
    class Forbidden(message: String?) : ErpError(message)

    class Http(val code: Int, message: String?) : ErpError(message) {
        val isNotFound: Boolean get() = code == 404
    }

    /** The server may have carried out the request even though no answer arrived. */
    val isOutcomeUnknown: Boolean
        get() = this is Network || (this is Http && code >= 500)

    companion object {
        fun from(throwable: Throwable): ErpError = when (throwable) {
            is ErpError -> throwable
            is InvalidSettingsException -> NotConfigured()
            is IOException -> Network(throwable)
            is SerializationException -> UnexpectedResponse(throwable)
            is HttpException -> fromHttp(throwable.code(), throwable.response()?.errorBody()?.string())
            else -> UnexpectedResponse(throwable)
        }

        fun fromHttp(code: Int, body: String?): ErpError {
            val message = body?.let(::serverMessage)
            return when (code) {
                401 -> Unauthorized()
                403 -> Forbidden(message)
                else -> Http(code, message)
            }
        }

        /**
         * Frappe sends `_server_messages` as a JSON string holding a list of JSON strings,
         * each an object with a `message` that may contain HTML.
         */
        internal fun serverMessage(body: String): String? = runCatching {
            val root = erpJson.parseToJsonElement(body).jsonObject
            val raw = root["_server_messages"]?.jsonPrimitive?.content
            val fromMessages = raw?.let { erpJson.parseToJsonElement(it).jsonArray }
                ?.mapNotNull { entry ->
                    val inner = (entry as? JsonPrimitive)?.content ?: return@mapNotNull null
                    val obj = erpJson.parseToJsonElement(inner) as? JsonObject
                    (obj?.get("message") as? JsonPrimitive)?.content
                }
                ?.map(::stripHtml)
                ?.filter { it.isNotBlank() }
                ?.joinToString("\n")
            fromMessages?.takeIf { it.isNotEmpty() }
        }.getOrNull()

        private fun stripHtml(text: String): String =
            text.replace(Regex("<[^>]*>"), "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .trim()
    }
}
