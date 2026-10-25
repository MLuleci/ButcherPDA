package bg.butcher.pda.hardware

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

// e.g. "\000026" (UTF-8), which the scan engine sends before QR codes that declare their character set
private val ECI_DESIGNATOR = Regex("""^\\\d{6}""")

sealed interface ScannedCode {
    data class Batch(val number: String) : ScannedCode
    data class Unrecognised(val raw: String) : ScannedCode
}

/**
 * Labels hold the batch's ERPNext page, `https://<host>/app/batch/<batch>`. Anything that isn't a
 * URL is taken as a plain batch number, e.g. a supplier's label. The host isn't checked, so labels
 * printed against another server still work; the lookup decides whether the batch exists.
 */
fun parseScan(raw: String): ScannedCode {
    val text = raw.trim().replace(ECI_DESIGNATOR, "")
    if (text.isEmpty()) return ScannedCode.Unrecognised(raw)
    if (!text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) {
        return ScannedCode.Batch(text)
    }

    val url = text.toHttpUrlOrNull() ?: return ScannedCode.Unrecognised(raw)
    // Already decoded, so "BATCH%2F001" gives "BATCH/001"
    val segments = url.pathSegments.filter { it.isNotEmpty() }
    return if (segments.size == 3 && segments[0] == "app" && segments[1] == "batch") {
        ScannedCode.Batch(segments[2])
    } else {
        ScannedCode.Unrecognised(raw)
    }
}
