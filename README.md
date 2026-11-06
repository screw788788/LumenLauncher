# Lumen Launcher

A minimal, fast Android launcher with ColorOS-style looks and springy animations.
Built with Jetpack Compose. No ads, no tracking, no internet permission.

## Features
- Transparent home over your wallpaper, large clock + date (tap = alarms)
- Rounded-square icons, uniform across all apps (pre-masked, so scrolling stays smooth)
- Springy press animation on every icon; zoom-from-icon app launch
- Frosted floating dock (4 slots)
- Swipe up anywhere for the app drawer: it follows your finger, then springs open/closed on release
- Drawer search (press Go to launch the first match); pull down at the top of the list to close
- Swipe down on home to open the notification shade (best effort, depends on the device)
- Long-press menu: add/remove from Home or Dock, App info, Uninstall
- Multi-page home with an animated page indicator
- Live updates when apps are installed/removed
- Release build is R8-minified and resource-shrunk

Not included (on purpose, to stay light): widgets, folders, icon packs, work profile apps.

## Get the APK from GitHub (no Android Studio needed)
1. Create a new empty repo on GitHub.
2. Push this folder:
   ```
   git init && git add . && git commit -m "Lumen Launcher"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
3. Open the repo's **Actions** tab and wait for "Build APK" to finish (~4-6 min first time).
4. Download the APK from either:
   - **Releases -> "Lumen Launcher (latest build)"** (updated on every push to main), or
   - the **Lumen-Launcher-apk** artifact on the workflow run.

For a versioned release: `git tag v1.0 && git push --tags`.

## Install
1. Open the APK on your phone and allow "install unknown apps".
2. Press Home -> choose **Lumen** -> **Always**
   (or Settings -> Apps -> Default apps -> Home app).

## Build locally
Open in Android Studio (Ladybug or newer), or run `./gradlew :app:assembleRelease`
(needs JDK 17 and the Android SDK).

## Notes
- The release APK is signed with the debug key so it installs immediately. If you plan to
  publish it anywhere, create your own keystore and set it in `app/build.gradle.kts`.
- Tuning knobs: icon size `ICON_DP` (LauncherViewModel.kt), drawer spring in `DrawerState.kt`,
  columns / row height at the top of `LauncherScreen.kt`.
