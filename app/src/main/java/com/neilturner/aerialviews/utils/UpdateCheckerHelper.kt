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

sealed interface UpdateCheckResult {
    data class Available(val updateInfo: UpdateInfo) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data object Failed : UpdateCheckResult
}

object UpdateCheckerHelper {
    private const val GITHUB_REPO = "naveeneppalapally/AerialViews-release-test"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val GITHUB_LATEST_URL = "https://github.com/$GITHUB_REPO/releases/latest"

    /** Fetches the latest GitHub release; falls back to the public latest redirect if the API is unavailable. */
    suspend fun checkForUpdate(currentVersion: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val latest = fetchLatestReleaseInfo(currentVersion) ?: return@withContext UpdateCheckResult.Failed
                if (isNewerVersion(latest.tagName, currentVersion)) {
                    UpdateCheckResult.Available(latest)
                } else {
                    UpdateCheckResult.UpToDate
                }
            } catch (e: Exception) {
                Timber.e(e, "UpdateChecker: check failed")
                UpdateCheckResult.Failed
            }
        }

    private fun fetchLatestReleaseInfo(currentVersion: String): UpdateInfo? =
        fetchLatestReleaseInfoFromApi(currentVersion) ?: fetchLatestReleaseInfoFromRedirect(currentVersion)

    private fun fetchLatestReleaseInfoFromApi(currentVersion: String): UpdateInfo? {
        val conn = URL(GITHUB_API_URL).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.setRequestProperty("User-Agent", "AerialViewsPlus/$currentVersion")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != 200) {
                Timber.w("UpdateChecker: API HTTP ${conn.responseCode}")
                return null
            }
            val body = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
            val apkUrl =
                json.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length())
                        .map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk") }
                        ?.optString("browser_download_url")
                } ?: return null
            UpdateInfo(tag, apkUrl)
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchLatestReleaseInfoFromRedirect(currentVersion: String): UpdateInfo? {
        val conn = URL(GITHUB_LATEST_URL).openConnection() as HttpURLConnection
        return try {
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "AerialViewsPlus/$currentVersion")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val location = conn.getHeaderField("Location")
            if (location.isNullOrBlank()) {
                Timber.w("UpdateChecker: latest redirect missing Location header (HTTP ${conn.responseCode})")
                return null
            }
            val tag = location.substringAfterLast('/').takeIf { it.startsWith("v") } ?: return null
            val downloadUrl = "https://github.com/$GITHUB_REPO/releases/download/$tag/AerialViews-Plus-$tag.apk"
            UpdateInfo(tag, downloadUrl)
        } finally {
            conn.disconnect()
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
