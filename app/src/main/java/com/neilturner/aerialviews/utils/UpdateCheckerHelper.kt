package com.neilturner.aerialviews.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val tagName: String,
    val downloadUrl: String,
)

object UpdateCheckerHelper {
    private const val GITHUB_REPO = "naveeneppalapally/AerialViews-release-test"

    /** Fetches the latest GitHub release; returns [UpdateInfo] if [currentVersion] is older. */
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
                val conn = url.openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    if (conn.responseCode != 200) {
                        Timber.w("UpdateChecker: HTTP ${conn.responseCode}")
                        return@withContext null
                    }
                    val body = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return@withContext null
                    val apkUrl = json.optJSONArray("assets")?.let { assets ->
                        (0 until assets.length())
                            .map { assets.getJSONObject(it) }
                            .firstOrNull { it.optString("name").endsWith(".apk") }
                            ?.optString("browser_download_url")
                    } ?: return@withContext null
                    if (isNewerVersion(tag, currentVersion)) UpdateInfo(tag, apkUrl) else null
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "UpdateChecker: check failed")
                null
            }
        }

    /**
     * Enqueues a download of the APK via [DownloadManager].
     * Returns the download ID so the caller can track progress if needed.
     */
    fun enqueueDownload(
        context: Context,
        updateInfo: UpdateInfo,
    ): Long {
        val fileName = "AerialViews-${updateInfo.tagName}.apk"
        val request =
            DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
                .setTitle("AerialViews ${updateInfo.tagName}")
                .setDescription("Downloading update…")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(request)
    }

    fun isNewerVersion(
        tagName: String,
        currentVersion: String,
    ): Boolean {
        val remote = tagName.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val local = currentVersion.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
