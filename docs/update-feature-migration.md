# Update Dialog Feature — Migration Guide

How to port the self-update dialog from `AerialViews-release-test` to the main `AerialViews-plus` repo.

---

## What the feature does

When the app starts, it silently checks GitHub for a newer release.
If one exists and the user hasn't already dismissed it, a full-screen dialog appears offering a one-tap download and install.

- **"Download update"** → queues a system `DownloadManager` download; auto-installs the APK via `ACTION_INSTALL_PACKAGE` when done.
- **"Later"** → suppresses the dialog **for that exact version only** (see [Later behavior](#later-button-behavior) below).
- The update check runs once per app session.

---

## Later button behavior

Pressing **Later** saves the current release tag to `UpdatePrefs.homeUpdatePromptDismissedTag` (e.g. `"v1.3.22"`).

| Event | What happens |
|---|---|
| User presses Later on v1.3.22 | Tag `"v1.3.22"` is saved; dialog is suppressed on every subsequent app start while v1.3.22 is still the latest. |
| A new release v1.3.23 is published | The tag won't match → dialog shows again immediately on next launch. |
| User installs the update they dismissed | `clearDismissedUpdateIfInstalled()` detects the installed version equals the dismissed tag and clears it (so the dismissed state doesn't linger). |

**There is no time-based cooldown.** Later = permanent per-version suppression. It only resets when a newer version is released.

---

## Files to copy

### New files (don't exist in main repo yet)

| Source path (test repo) | Destination (main repo) |
|---|---|
| `app/src/main/java/…/utils/UpdateCheckerHelper.kt` | same path |
| `app/src/main/java/…/utils/HomeUpdatePromptHelper.kt` | same path |
| `app/src/main/java/…/models/prefs/UpdatePrefs.kt` | same path |
| `app/src/main/res/layout/dialog_update_prompt.xml` | same path |
| `app/src/main/res/drawable/update_prompt_panel_background.xml` | same path |
| `app/src/main/res/drawable/update_prompt_left_panel_background.xml` | same path |
| `app/src/main/res/drawable/update_prompt_badge_background.xml` | same path |
| `app/src/main/res/drawable/update_prompt_button_primary_background.xml` | same path |
| `app/src/main/res/drawable/update_prompt_button_secondary_background.xml` | same path |

### Modified files (merge changes, don't overwrite)

| File | What to add |
|---|---|
| `app/src/main/AndroidManifest.xml` | `REQUEST_INSTALL_PACKAGES` permission |
| `app/src/main/res/values/colors.xml` | All `update_prompt_*` color tokens |
| `app/src/main/res/values/strings.xml` | All `home_update_*` string resources |
| `app/src/main/java/…/ui/MainFragment.kt` | `maybeShowStartupUpdatePrompt()` call + implementation |
| `app/src/main/java/…/ui/MainActivity.kt` | `startAppUpdateDownload()`, `DownloadManager` receiver |

---

## Step-by-step instructions

### 1. Copy utility files

Copy these files verbatim from the test repo into the main repo (same relative path under `app/src/main/java/com/neilturner/aerialviews/`):

- `utils/UpdateCheckerHelper.kt` — GitHub API call, version comparison, APK download queue, install trigger
- `utils/HomeUpdatePromptHelper.kt` — inflates and configures the dialog; formats release notes (strips markdown, converts `- item` to `• item`, handles literal `\n`)
- `models/prefs/UpdatePrefs.kt` — single Kotpref property `homeUpdatePromptDismissedTag` that stores the suppressed version tag

> **Note:** `UpdatePrefs` uses `kotprefName = "${context.packageName}_preferences"` which is the same shared preferences file as `GeneralPrefs`. This is intentional and consistent with the project convention.

### 2. Update the GitHub repo constant

In `UpdateCheckerHelper.kt`, change: 
```kotlin
private const val GITHUB_REPO = "naveeneppalapally/AerialViews-release-test"
```
to: 
```kotlin
private const val GITHUB_REPO = "naveeneppalapally/AerialViews-plus"   // or whatever the main repo slug is
```

### 3. Copy layout + drawables

Copy these files verbatim into `app/src/main/res/`:

**Layout:**
- `layout/dialog_update_prompt.xml`

**Drawables (4 box-type drawables):**
- `drawable/update_prompt_panel_background.xml` — outer dialog rounded rect
- `drawable/update_prompt_left_panel_background.xml` — left branding panel (pure black)
- `drawable/update_prompt_badge_background.xml` — pill badge (shared by both "NEW VERSION AVAILABLE" and "VERSION STABLE")
- `drawable/update_prompt_button_primary_background.xml` — "Download update" button (selector: default/focused/pressed)
- `drawable/update_prompt_button_secondary_background.xml` — "Later" button (ghost: transparent default, white highlight on focus)

> The dialog uses **4 visible box types**: outer panel, left branding panel, pill badge, primary button. The "Later" button has no visible box in its default state — it appears as plain text and only shows a highlight when the D-pad focuses it.

### 4. Add color tokens

Add all `update_prompt_*` entries from the test repo's `res/values/colors.xml` into the main repo's `colors.xml`:

```xml
<color name="update_prompt_surface_top">#F0101010</color>
<color name="update_prompt_surface_bottom">#F0101010</color>
<color name="update_prompt_surface_stroke">#22FFFFFF</color>
<color name="update_prompt_text_primary">#FFFFFFFF</color>
<color name="update_prompt_text_secondary">#AAFFFFFF</color>
<color name="update_prompt_text_accent">#FFFFFFFF</color>
<color name="update_prompt_accent_soft">#18FFFFFF</color>
<color name="update_prompt_button_primary">#FF1A1A1A</color>
<color name="update_prompt_button_primary_focused">#FF333333</color>
<color name="update_prompt_button_primary_pressed">#FF222222</color>
<color name="update_prompt_left_surface">#F0000000</color>
```

### 5. Add string resources

Add these strings to `res/values/strings.xml`:

```xml
<string name="home_update_title">New release</string>
<string name="home_update_version">AerialViews+ %1$s</string>
<string name="home_update_summary">Version %2$s is available. You can keep using the app while it downloads.</string>
<string name="home_update_whats_new">Release notes</string>
<string name="home_update_empty_notes">Bug fixes and improvements</string>
<string name="home_update_download">Download update</string>
<string name="home_update_later">Later</string>
<string name="home_update_download_started">Downloading AerialViews+ %1$s…</string>
<string name="home_update_download_failed">Couldn\'t start the update download</string>
<string name="home_update_badge">New version available</string>
<string name="home_update_app_name">AerialViews+</string>
<string name="home_update_highlights">Release highlights</string>
<string name="home_update_version_stable">Version stable</string>
```

### 6. Add manifest permission

In `AndroidManifest.xml`, add alongside the existing `INTERNET` permission:

```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

### 7. Wire up MainFragment

In `MainFragment.kt`, add the startup update check. The simplest place is in `onResume` or after the fragment is fully attached.

**Add the import:**
```kotlin
import com.neilturner.aerialviews.models.prefs.UpdatePrefs
import com.neilturner.aerialviews.utils.HomeUpdatePromptHelper
import com.neilturner.aerialviews.utils.UpdateCheckResult
import com.neilturner.aerialviews.utils.UpdateCheckerHelper
```

**Add a guard field at the top of the fragment class:**
```kotlin
private var hasCheckedStartupUpdate = false
```

**Add the method:**
```kotlin
fun maybeShowStartupUpdatePrompt() {
    if (hasCheckedStartupUpdate || !isAdded || parentFragmentManager.isStateSaved) return
    hasCheckedStartupUpdate = true
    clearDismissedUpdateIfInstalled()

    viewLifecycleOwner.lifecycleScope.launch {
        when (val result = UpdateCheckerHelper.checkForUpdate(BuildConfig.VERSION_NAME)) {
            is UpdateCheckResult.Available -> {
                if (UpdatePrefs.homeUpdatePromptDismissedTag == result.updateInfo.tagName) return@launch
                HomeUpdatePromptHelper.show(
                    context = requireContext(),
                    currentVersion = BuildConfig.VERSION_NAME,
                    updateInfo = result.updateInfo,
                    onDownload = {
                        UpdatePrefs.homeUpdatePromptDismissedTag = ""
                        val mainActivity = activity as? MainActivity
                        mainActivity?.startAppUpdateDownload(result.updateInfo)
                    },
                    onLater = {
                        UpdatePrefs.homeUpdatePromptDismissedTag = result.updateInfo.tagName
                    },
                )
            }
            UpdateCheckResult.UpToDate, UpdateCheckResult.Failed -> Unit
        }
    }
}

private fun clearDismissedUpdateIfInstalled() {
    if (UpdatePrefs.homeUpdatePromptDismissedTag.removePrefix("v") == BuildConfig.VERSION_NAME) {
        UpdatePrefs.homeUpdatePromptDismissedTag = ""
    }
}
```

**Call it from `onResume`** (or wherever the home screen becomes visible):
```kotlin
override fun onResume() {
    super.onResume()
    // … existing code …
    maybeShowStartupUpdatePrompt()
}
```

### 8. Wire up MainActivity

**Add imports:**
```kotlin
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.neilturner.aerialviews.utils.ToastHelper
import com.neilturner.aerialviews.utils.UpdateCheckerHelper
import com.neilturner.aerialviews.utils.UpdateInfo
import kotlinx.coroutines.launch
```

**Add fields:**
```kotlin
private var updateDownloadId: Long = -1L
private var isDownloadReceiverRegistered = false

private val downloadReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id != updateDownloadId) return
        val didLaunch = UpdateCheckerHelper.installDownloadedApk(this@MainActivity, updateDownloadId)
        if (!didLaunch) {
            lifecycleScope.launch { ToastHelper.show(this@MainActivity, R.string.home_update_download_failed) }
        }
        updateDownloadId = -1L
    }
}
```

**Add the download method:**
```kotlin
fun startAppUpdateDownload(updateInfo: UpdateInfo) {
    updateDownloadId = UpdateCheckerHelper.enqueueDownload(this, updateInfo)
}
```

**Register/unregister the broadcast receiver in `onResume`/`onPause`:**
```kotlin
override fun onResume() {
    super.onResume()
    registerDownloadReceiver()
}

override fun onPause() {
    super.onPause()
    unregisterDownloadReceiver()
}

private fun registerDownloadReceiver() {
    if (isDownloadReceiverRegistered) return
    ContextCompat.registerReceiver(
        this, downloadReceiver,
        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    isDownloadReceiverRegistered = true
}

private fun unregisterDownloadReceiver() {
    if (!isDownloadReceiverRegistered) return
    try { unregisterReceiver(downloadReceiver) } catch (_: Exception) {}
    isDownloadReceiverRegistered = false
}
```

---

## CI/CD automation (GitHub Actions)

The test repo uses a `workflow_dispatch` build workflow (`.github/workflows/build.yml`) that:

1. Takes `version` and `release_notes` as inputs
2. Builds a signed release APK (`assembleGithubNonMinifiedRelease`)
3. Verifies the APK signer fingerprint against a pinned SHA-256
4. Generates an `update-metadata.json` file alongside the APK:
   ```json
   {
     "tagName": "v1.3.22",
     "versionName": "1.3.22",
     "apkFileName": "AerialViews-Plus-v1.3.22.apk",
     "downloadUrl": "https://github.com/…/releases/download/v1.3.22/AerialViews-Plus-v1.3.22.apk",
     "releaseNotes": "• New feature\n• Bug fix",
     "publishedAt": "2026-04-06T00:00:00Z"
   }
   ```
5. Creates a GitHub Release with the APK and metadata file attached

The `UpdateCheckerHelper` first tries the GitHub API (`/repos/:owner/:repo/releases/latest`), falls back to the latest release redirect, and then fetches `update-metadata.json` from the release assets for structured release notes.

**To reuse this workflow in the main repo:**
- Copy `.github/workflows/build.yml`
- Update `GITHUB_REPO` constant and the `APK` filename pattern in the workflow
- Add repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
- Update `EXPECTED_SIGNER_SHA256` to your release keystore fingerprint

---

## Summary of changes by file count

| Category | Files |
|---|---|
| New Kotlin files | 3 (`UpdateCheckerHelper`, `HomeUpdatePromptHelper`, `UpdatePrefs`) |
| New layout | 1 (`dialog_update_prompt.xml`) |
| New drawables | 5 (panel, left panel, badge, primary button, secondary button) |
| Modified resource files | 2 (`colors.xml`, `strings.xml`) |
| Modified Kotlin files | 2 (`MainFragment`, `MainActivity`) |
| Modified manifest | 1 (add `REQUEST_INSTALL_PACKAGES`) |
| CI workflow | 1 (`build.yml`) |
| **Total** | **15 files** |
