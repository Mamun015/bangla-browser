<div align="center">

<img src="docs/icon.png" width="104" alt="Bangla Browser app icon">

# বাংলা ব্রাউজার · Bangla Browser

**A fast, light Android browser with a full Bengali interface.**
WebView based. No ads. No tracking. About 100 KB.

[**⬇ Download APK**](https://github.com/Mamun015/bangla-browser/releases/latest) ·
[Website](https://mamun015.github.io/bangla-browser/) ·
[Features](#features) ·
[Install](#install) ·
[Build](#build-from-source)

![Android 5.0+](https://img.shields.io/badge/Android-5.0%2B-3ddc84?logo=android&logoColor=white)
![Version](https://img.shields.io/badge/version-1.1.04-006a4e)
![License](https://img.shields.io/badge/license-MPL--2.0-blue)
![Size](https://img.shields.io/badge/APK-105%20KB-lightgrey)

</div>

![Bangla Browser screenshots](docs/screenshot.png)

বাংলা ভাষার জন্য বানানো একটি ছোট আর দ্রুত অ্যান্ড্রয়েড ব্রাউজার। পুরো ইন্টারফেস বাংলায়, বিজ্ঞাপন নেই, ট্র্যাকিং নেই। ট্যাব, বুকমার্ক, ইতিহাস, রাতের মোড — দৈনন্দিন ব্রাউজিংয়ের যা দরকার, সব একসাথে।

## Features

| | |
|---|---|
| **বাংলা ইন্টারফেস** | Every button, menu and message is in Bengali. |
| **Tabs** | Up to 8 tabs with a Chrome style switcher and a tab counter in the toolbar. |
| **Edge style menu** | A bottom sheet with new tab, bookmarks, history, downloads, share, desktop site and dark mode. |
| **Search built in** | Type a word to search Google, DuckDuckGo or Bing; type an address to open the site. |
| **Bookmarks & history** | Saved locally on the device, with one-tap clear options. |
| **Dark mode** | Full dark theme, including the built-in home page. |
| **Downloads & uploads** | Android Download Manager for files, file picker for uploads. |
| **Session restore** | Reopen the app and your tabs are still there. |
| **Settings** | Search engine, text size, JavaScript, images, clear history, bookmarks, cookies and cache. |

## Install

1. Download **[BanglaBrowser-1.1.04.apk](https://github.com/Mamun015/bangla-browser/releases/latest)**.
2. Tap the file in your phone's Downloads folder.
3. Allow installs from this source when Android asks, then tap **Install**.

The APK is signed with a self-generated key, so Android shows the usual "unknown source" warning. Updates install over older versions and keep your bookmarks and history.

Requirements: Android 5.0 (API 21) or newer.

## Screenshots

The image at the top shows the home page, the bottom menu, settings and the About dialog. Open [`docs/preview.html`](docs/preview.html) for the full-size mockup.

## Build from source

No Gradle and no Android Studio. A shell script compiles, dexes, aligns and signs the APK with the standard Android build tools.

Requirements:

- JDK 17
- Android SDK build-tools (`aapt`, `zipalign`, `apksigner`)
- `sdk/android.jar` (API 33) and `sdk/r8.jar` in the project folder

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 bash build.sh
```

Output: `BanglaBrowser-1.1.04.apk`. See [BUILD.md](BUILD.md) for details. The signing key (`keystore.jks`) is not in the repository; generate your own to build a fork.

## Limitations

- Google blocks account sign-in inside WebView, so Gmail and YouTube logins open in Chrome instead.
- DRM video (Netflix, Prime Video) does not play.
- A few sites refuse to load inside WebView.
- The app is distributed as a direct APK, not through Google Play.

## FAQ

**Is it free?** Yes. Open source under MPL-2.0, no ads, no in-app purchases.

**Does it collect data?** No analytics or tracking. Bookmarks, history and settings stay on the device.

**Will I lose my data when updating?** No. Install the new APK over the old one; bookmarks, history and settings are kept.

**Why does Android warn about the installer?** The app is signed outside the Play Store. Tap "Install anyway" / allow the source once.

**Can I use it on Android 5?** Yes, Android 5.0 and newer.

## License

[Mozilla Public License 2.0](LICENSE).

In short: anyone may use, change and share the code. If you modify a file and
distribute it, that file stays under the MPL, so nobody can take this code and
close it. The name and icon are separate; see [TRADEMARK.md](TRADEMARK.md).

Versions released before 1.1.02 (1.0, 1.1, 1.1.01) were published under the MIT
license and stay under it.

<div align="center">

Made by [Mamun (@Mamun015)](https://github.com/Mamun015)

**Made in Bangladesh** 🇧🇩

</div>
