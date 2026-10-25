package bg.butcher.pda.data

import bg.butcher.pda.model.GithubRelease
import bg.butcher.pda.network.erpJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import kotlin.coroutines.resumeWithException

/** The project's latest release on GitHub. The repository is public, so no token is needed. */
val LATEST_RELEASE_URL = "https://api.github.com/repos/MLuleci/ButcherPDA/releases/latest".toHttpUrl()

/** A release APK newer than the installed app. */
data class AvailableUpdate(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val size: Long,
)

// ButcherPDA-<versionCode>-<versionName>.apk, e.g. ButcherPDA-57-1b641ad.apk
private val ASSET_NAME = Regex("""ButcherPDA-(\d+)-([0-9a-f]+)\.apk""")

/** The release's APK if it's newer than [installedVersionCode], or null. Assets named any other way are ignored. */
fun GithubRelease.newerThan(installedVersionCode: Int): AvailableUpdate? = assets
    .mapNotNull { asset ->
        val match = ASSET_NAME.matchEntire(asset.name) ?: return@mapNotNull null
        val code = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
        AvailableUpdate(code, match.groupValues[2], asset.downloadUrl, asset.size)
    }
    .maxByOrNull { it.versionCode }
    ?.takeIf { it.versionCode > installedVersionCode }

class UpdateException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** The response, without blocking a thread while waiting for it. Cancelling the coroutine cancels the call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            // A response that arrives after cancelling is closed rather than leaked
            continuation.resume(response) { _, value, _ -> value.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
}

/** Runs [block] on the response off the main thread, and closes it. */
private suspend fun <T> OkHttpClient.fetch(request: Request, block: suspend (Response) -> T): T =
    newCall(request).await().use { response -> withContext(Dispatchers.IO) { block(response) } }

/**
 * Checks GitHub Releases for a newer APK and downloads it into [directory]. [client] must not be the ERP client,
 * whose interceptor would send the request to the ERP server instead.
 */
class UpdateRepository(
    private val client: OkHttpClient,
    private val directory: File,
    private val installedVersionCode: Int,
    private val latestReleaseUrl: HttpUrl = LATEST_RELEASE_URL,
) {
    /** A newer release, or null when the app is up to date or nothing has been released yet. */
    suspend fun checkForUpdate(): AvailableUpdate? {
        val request = Request.Builder()
            .url(latestReleaseUrl)
            .header("Accept", "application/vnd.github+json")
            .build()
        return client.fetch(request) { response ->
            when {
                // No release has been published yet
                response.code == 404 -> null
                !response.isSuccessful -> throw UpdateException("GitHub answered ${response.code}")
                else -> {
                    val body = response.body.string()
                    val release = runCatching { erpJson.decodeFromString<GithubRelease>(body) }
                        .getOrElse { throw UpdateException("Unexpected release data", it) }
                    release.newerThan(installedVersionCode)
                }
            }
        }
    }

    /**
     * Downloads [update]'s APK, replacing any earlier download, and checks it arrived whole. [onProgress] gets the
     * fraction done, from 0 to 1.
     */
    suspend fun download(update: AvailableUpdate, onProgress: (Float) -> Unit = {}): File {
        val target = File(directory, "ButcherPDA-${update.versionCode}.apk")
        val request = Request.Builder().url(update.downloadUrl).build()
        client.fetch(request) { response ->
            if (!response.isSuccessful) throw UpdateException("Download answered ${response.code}")
            directory.mkdirs()
            directory.listFiles()?.forEach { it.delete() }
            var copied = 0L
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        // Leaving Settings stops a download part-way
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (update.size > 0) onProgress((copied.toFloat() / update.size).coerceAtMost(1f))
                    }
                }
            }
            if (update.size > 0 && copied != update.size) {
                target.delete()
                throw UpdateException("Downloaded $copied of ${update.size} bytes")
            }
        }
        return target
    }
}
