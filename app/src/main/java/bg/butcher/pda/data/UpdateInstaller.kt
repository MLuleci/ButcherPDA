package bg.butcher.pda.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
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
     */
    fun installIntent(apk: File): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
