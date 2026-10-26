package bg.butcher.pda.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

/** Why a downloaded APK can't be installed over this app. */
enum class ApkProblem { UNREADABLE, OTHER_APP, WRONG_VERSION, WRONG_SIGNATURE }

/**
 * Checks a downloaded APK against the installed app and hands it to the system installer. The installer refuses a
 * different signature too, but only after it has opened, so the check is done here first.
 */
class UpdateInstaller(private val context: Context) {
    private val packageManager = context.packageManager

    // GET_SIGNATURES is the only flag on API 23; GET_SIGNING_CERTIFICATES is 28+
    @Suppress("DEPRECATION")
    private fun signatures(info: android.content.pm.PackageInfo?): Set<Signature>? = info?.signatures?.toSet()

    /**
     * Null when [apk] is this app at [versionCode], signed with the same key. Some Android versions don't read an
     * archive's signatures; then only the installer can tell, so that isn't treated as a mismatch.
     */
    @Suppress("DEPRECATION")
    fun check(apk: File, versionCode: Int): ApkProblem? {
        val archive = packageManager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNATURES)
            ?: return ApkProblem.UNREADABLE
        if (archive.packageName != context.packageName) return ApkProblem.OTHER_APP
        if (archive.versionCode != versionCode) return ApkProblem.WRONG_VERSION
        val installed = packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        val theirs = signatures(archive)
        if (!theirs.isNullOrEmpty() && theirs != signatures(installed)) return ApkProblem.WRONG_SIGNATURE
        return null
    }

    /**
     * Opens the system installer for [apk]. On Android 6 it asks to allow unknown sources itself when that's off;
     * Android 8+ asks per app, which the manifest's REQUEST_INSTALL_PACKAGES allows.
     *
     * Android 6's installer only takes `file://` URIs, and reads the path itself, so [apk] must be somewhere other
     * apps can read ([updateDirectory]). From Android 7 a `file://` URI throws, so a content URI is used instead.
     * ACTION_INSTALL_PACKAGE is deprecated from Android 10 in favour of PackageInstaller sessions, but still works
     * there, and is the way on Android 6.
     */
    @Suppress("DEPRECATION")
    fun installIntent(apk: File): Intent {
        val uri: Uri = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Uri.fromFile(apk)
        } else {
            FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        }
        return Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/**
 * Where updates are downloaded. Android 6's installer reads the file itself, so there it's the app's external files
 * folder, which other apps can read without the app needing a storage permission. Private cache otherwise, or when
 * external storage isn't mounted.
 */
fun updateDirectory(context: Context): File {
    val external = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) context.getExternalFilesDir(null) else null
    return File(external ?: context.cacheDir, "updates")
}
