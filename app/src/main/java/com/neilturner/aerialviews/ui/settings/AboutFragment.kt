@file:Suppress("SameReturnValue")

package com.neilturner.aerialviews.ui.settings

import android.content.pm.PackageInfo
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.neilturner.aerialviews.BuildConfig
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.ui.MainActivity
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.UpdateCheckResult
import com.neilturner.aerialviews.utils.UpdateInfo
import com.neilturner.aerialviews.utils.getPackageInfoCompat
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DateFormat
import java.util.Date

class AboutFragment : MenuStateFragment() {

    private var pendingUpdate: UpdateInfo? = null

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
            when (val result = UpdateCheckerHelper.checkForUpdate(BuildConfig.VERSION_NAME)) {
                is UpdateCheckResult.Available -> {
                    pendingUpdate = result.updateInfo
                    pref.summary = getString(R.string.about_update_available, result.updateInfo.tagName)
                    pref.isEnabled = true
                }

                UpdateCheckResult.UpToDate -> {
                    pendingUpdate = null
                    pref.summary = getString(R.string.about_update_uptodate)
                    pref.isEnabled = true
                }

                UpdateCheckResult.Failed -> {
                    pendingUpdate = null
                    pref.summary = getString(R.string.about_update_failed)
                    pref.isEnabled = true
                }
            }
        }
    }

    private fun startDownload(update: UpdateInfo) {
        val pref = findPreference<Preference>("about_update") ?: return
        pref.summary = getString(R.string.about_update_downloading)
        pref.isEnabled = false
        val mainActivity = activity as? MainActivity
        if (mainActivity == null) {
            Timber.e("UpdateChecker: MainActivity unavailable for About download")
            pref.summary = getString(R.string.about_update_failed)
            pref.isEnabled = true
            return
        }
        mainActivity.startAppUpdateDownload(update)
        pref.isEnabled = true
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
