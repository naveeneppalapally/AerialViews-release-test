package com.neilturner.aerialviews.utils

import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.MessageEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.kosert.flowbus.GlobalBus
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

object UpdateCheckerHelper {
    private const val GITHUB_REPO = "naveeneppalapally/AerialViews-release-test"
    private const val CHECK_INTERVAL_MS = 5 * 60 * 1000L // re-check every 5 minutes
    private const val INITIAL_DELAY_MS = 30_000L          // first check after 30 s

    suspend fun startChecking() {
        // Ensure MESSAGE1 is assigned to the top-left slot so the banner is visible.
        // This must run BEFORE delay() so it takes effect before overlay initialisation
        // in the sibling coroutine that was launched immediately after this one.
        if (GeneralPrefs.slotTopLeft1 == OverlayType.EMPTY) {
            GeneralPrefs.slotTopLeft1 = OverlayType.MESSAGE1
        }
        delay(INITIAL_DELAY_MS)
        while (true) {
            try {
                val latestTag = fetchLatestTag()
                if (latestTag != null && isNewerVersion(latestTag, BuildConfig.VERSION_NAME)) {
                    Timber.i("UpdateChecker: new version $latestTag > ${BuildConfig.VERSION_NAME}")
                    GlobalBus.post(
                        MessageEvent(
                            type = OverlayType.MESSAGE1,
                            text = "⬆ Update available: $latestTag  (installed: v${BuildConfig.VERSION_NAME})",
                        ),
                    )
                } else {
                    Timber.i("UpdateChecker: up to date (${BuildConfig.VERSION_NAME})")
                }
            } catch (e: Exception) {
                Timber.e(e, "UpdateChecker: check failed")
            }
            delay(CHECK_INTERVAL_MS)
        }
    }

    private suspend fun fetchLatestTag(): String? =
        withContext(Dispatchers.IO) {
            val url = URL("https://api.github.com/repos/$GITHUB_REPO/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    JSONObject(body).optString("tag_name").takeIf { it.isNotBlank() }
                } else {
                    Timber.w("UpdateChecker: HTTP ${conn.responseCode}")
                    null
                }
            } finally {
                conn.disconnect()
            }
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
