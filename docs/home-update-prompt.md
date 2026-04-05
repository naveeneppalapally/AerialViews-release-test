# Home-Screen Update Prompt

## Goal

Show a startup update prompt on the main AerialViews+ home screen when a newer GitHub release is available.

The prompt should:

- appear after app launch on the home screen
- feel like part of AerialViews+, not a generic system dialog
- work on Android TV with D-pad focus
- show changelog text for the new release
- let the user start a flexible download-and-install flow
- degrade cleanly when the GitHub API is rate-limited

## Terminology

The closest standard term is a flexible in-app update prompt.

Google Play documents two update patterns:

- immediate update: blocking, full-screen, update now before continuing
- flexible update: prompt first, let the user continue using the app while the update downloads

For AerialViews+, the correct fit is the flexible pattern because updates are useful but not critical to core playback. The app should prompt, not trap.

## Research Notes

Common patterns used by large Android apps and Android guidance:

- show the update prompt only after the main UI is ready, not during splash or app bootstrap
- keep the primary action obvious: update or download
- keep one low-friction secondary action: later or dismiss
- show a short summary of what changed before the user commits
- avoid full-screen interruption for non-critical updates
- on TV, every action must be clearly focusable and navigable with D-pad states

Android TV focus guidance also reinforces that focusable actions need a strong focused state, and only one actionable element should be focused at a time.

## Release Sequence

To make `1.3.6` appear as a startup prompt, the prompt logic must already exist in `1.3.5`.

That means the practical sequence is:

1. implement the home-screen prompt in `1.3.5`
2. publish `1.3.5`
3. publish `1.3.6` with release metadata and changelog text
4. install `1.3.5` later and verify it prompts for `1.3.6`

This is the only sequence that makes the startup prompt technically possible.

## UX Design

### Prompt placement

- trigger from the main home fragment after launch logic finishes
- only show on the root home screen
- do not show when the app is auto-jumping straight into screensaver mode
- do not show when the app was opened for import/export intent handling

### Prompt layout

Use a custom dialog with:

- title: update available
- version line: current version to new version
- short helper line: new features and fixes are ready to install
- changelog section: scrollable text pulled from release metadata
- primary action: download and install
- secondary action: later

### AerialViews+ visual direction

The app already leans on:

- dark surfaces
- light text
- thin, calm typography
- simple uncluttered composition

The prompt should follow that instead of introducing bright Material defaults. The dialog should use a dark panel, soft border, subtle highlight color, and large TV-safe buttons with clear focused state.

## Technical Design

### Release metadata asset

Each GitHub release should publish a small JSON file alongside the APK.

Proposed asset name:

- `update-metadata.json`

Proposed structure:

```json
{
  "tagName": "v1.3.6",
  "versionName": "1.3.6",
  "apkFileName": "AerialViews-Plus-v1.3.6.apk",
  "downloadUrl": "https://github.com/.../AerialViews-Plus-v1.3.6.apk",
  "releaseNotes": "Home-screen update prompt\nBetter release note handling",
  "publishedAt": "2026-04-05T00:00:00Z"
}
```

Why this exists:

- GitHub API body text is rich but rate-limited
- redirect fallback is stable for discovering the latest tag
- metadata asset keeps changelog delivery public, simple, and independent from the API

### Update fetch strategy

Order of operations:

1. try GitHub API for latest release
2. if API succeeds, use its tag, APK asset URL, and release body
3. if API fails, resolve the latest tag through `releases/latest` redirect
4. fetch `update-metadata.json` from that tag
5. if metadata fetch fails, still allow update with direct APK URL and empty release notes

This preserves the existing rate-limit fix and adds changelog support.

### Shared update model

`UpdateInfo` should carry more than just tag and APK URL.

It should include:

- tag name
- download URL
- release notes

Optional:

- published timestamp

### Startup prompt state

Store prompt dismissal state in Kotpref so the dialog does not nag on every launch.

Needed internal fields:

- last dismissed update tag

Behavior:

- if user taps later, store the tag and do not re-show for that same version
- if a newer tag appears later, prompt again
- About screen can still show and install the same version even if the startup prompt was dismissed

### Download and install flow

Use the existing `DownloadManager` flow.

Behavior after the user taps download:

- enqueue the APK download
- keep Android's visible download notification
- optionally show a small progress dialog while the app stays usable
- when the download completes, launch the package installer

This matches the flexible update pattern better than a blocking flow.

## App Integration Points

### Main entry point

- `MainActivity` decides whether startup flows should run
- `MainFragment` is the correct host for a home-screen update prompt once normal launch is confirmed

### Existing update code

- `UpdateCheckerHelper` remains the central fetch layer
- `AboutFragment` keeps its manual update entry point
- new home-screen prompt should use the same `UpdateInfo` model and download helper path

### Dialog implementation

Use a custom `AlertDialog` layout instead of stock buttons.

Reason:

- easier to style to match AerialViews+
- easier to make TV-safe buttons with explicit focused state
- easier to present release notes cleanly

## Validation Plan

1. publish `1.3.5` with startup prompt logic included
2. publish `1.3.6` with changelog metadata asset
3. later install `1.3.5`
4. open app to the home screen
5. verify the prompt appears automatically for `1.3.6`
6. verify changelog text is shown
7. verify `Later` dismisses only that version
8. verify `Download and install` starts the existing APK flow

## Non-Goals

- no forced full-screen update gate
- no Play Core dependency because this is a GitHub-release sideload flow
- no hidden background auto-install behavior
