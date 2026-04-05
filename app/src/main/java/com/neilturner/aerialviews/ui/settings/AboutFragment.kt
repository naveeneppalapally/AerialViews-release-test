@file:Suppress("SameReturnValue")

package com.neilturner.aerialviews.ui.settings

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.UpdateCheckerHelper
import com.neilturner.aerialviews.utils.UpdateInfo
import com.neilturner.aerialviews.utils.getPackageInfoCompat
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DateFormat
import java.util.Date

class AboutFragment : MenuStateFragment() {

    private var pendingUpdate: UpdateInfo? = null
    private var downloadId: Long = -1L

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == downloadId) installDownloadedApk(context)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_about, rootKey)
        updateVersionSummary()
        setupUpdatePreference()
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("About", this)
        updateVersionSummary()
        checkForUpdates()
        ContextCompat.registerReceiver(
            requireContext(),
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onPause() {
        super.onPause()
        runCatching { requireContext().unregisterReceiver(downloadReceiver) }
    }

    private fun setupUpdatePreference() {
        findPreference<Preference>("about_update")?.setOnPreferenceClickListener {
            val update = pendingUpdate
            if (update != null) {
                startDownload(update)
            } else {
                checkForUpdates()
            }
            true
        }
    }

    private fun checkForUpdates() {
        val pref = findPreference<Preference>("about_update") ?: return
        pref.summary = getString(R.string.about_update_checking)
        pref.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val update = UpdateCheckerHelper.checkForUpdate(BuildConfig.VERSION_NAME)
            pendingUpdate = update
            if (update != null) {
                pref.summary = getString(R.string.about_update_available, update.tagName)
                pref.isEnabled = true
            } else {
                pref.summary = getString(R.string.about_update_uptodate)
                pref.isEnabled = true
            }
        }
    }

    private fun startDownload(update: UpdateInfo) {
        val pref = findPreference<Preference>("about_update") ?: return
        pref.summary = getString(R.string.about_update_downloading)
        pref.isEnabled = false
        downloadId = UpdateCheckerHelper.enqueueDownload(requireContext(), update)
    }

    private fun installDownloadedApk(context: Context) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            Timber.e("UpdateChecker: downloaded file URI null for id=$downloadId")
            findPreference<Preference>("about_update")?.summary = getString(R.string.about_update_uptodate)
            findPreference<Preference>("about_update")?.isEnabled = true
            return
        }
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        context.startActivity(intent)
        findPreference<Preference>("about_update")?.isEnabled = true
    }

    private fun updateVersionSummary() {
        val context = context ?: return
        val version = findPreference<Preference>("about_version")
        val date = findPreference<Preference>("about_date")
        val packageInfo = runCatching {
            context.packageManager.getPackageInfoCompat(context.packageName, 0)
        }.getOrNull()

        version?.summary = buildVersionSummary(packageInfo)
        date?.title = getString(buildDateTitle(packageInfo))
        date?.summary = buildDateSummary(packageInfo)
    }

    private fun buildVersionSummary(packageInfo: PackageInfo?): String {
        val appName = getString(R.string.app_name)
        val versionCode = packageInfo?.longVersionCode ?: BuildConfig.VERSION_CODE.toLong()
        return "$appName ${BuildConfig.VERSION_NAME} ($versionCode)"
    }

    private fun buildDateTitle(packageInfo: PackageInfo?): Int =
        if (packageInfo != null && packageInfo.firstInstallTime != packageInfo.lastUpdateTime) {
            R.string.about_last_updated_title
        } else {
            R.string.about_installed_title
        }

    private fun buildDateSummary(packageInfo: PackageInfo?): String {
        val lastUpdate = packageInfo?.lastUpdateTime ?: BuildConfig.BUILD_TIME.toLong()
        val dateFormat = DateFormat.getDateTimeInstance()
        val date = Date(lastUpdate)
        return dateFormat.format(date)
    }
}
