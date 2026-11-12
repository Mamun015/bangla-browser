<div align="center">

<img src="docs/icon.png" width="104" alt="Bangla Browser app icon">

# বাংলা ব্রাউজার · Bangla Browser

**A fast, light Android browser with a full Bengali interface — and every English page turns into Bengali by itself.**
WebView based. No ads. No tracking. About 125 KB.

[**⬇ Download APK**](https://github.com/Mamun015/bangla-browser/releases/latest) ·
[Website](https://mamun015.github.io/bangla-browser/) ·
[Features](#features) ·
[Install](#install) ·
[Build](#build-from-source)

![Android 5.0+](https://img.shields.io/badge/Android-5.0%2B-3ddc84?logo=android&logoColor=white)
![Version](https://img.shields.io/badge/version-1.2.1-006a4e)
![License](https://img.shields.io/badge/license-MPL--2.0-blue)
![Size](https://img.shields.io/badge/APK-125%20KB-lightgrey)

</div>

![Bangla Browser screenshots](docs/screenshot.png)

বাংলা ভাষার জন্য বানানো একটি ছোট আর দ্রুত অ্যান্ড্রয়েড ব্রাউজার। পুরো ইন্টারফেস বাংলায়, বিজ্ঞাপন নেই, ট্র্যাকিং নেই। ট্যাব, বুকমার্ক, ইতিহাস, রাতের মোড — দৈনন্দিন ব্রাউজিংয়ের যা দরকার, সব একসাথে।

## যা এই ব্রাউজারটাকে আলাদা করে

| | |
|---|---|
| **🅰️ অটো বাংলা অনুবাদ** | যেকোনো ইংরেজি পেজ খুললেই লেখা নিজে থেকেই বাংলায় বদলে যায়, আলাদা অ্যাপ বা এক্সটেনশন ছাড়াই। ডিফল্টে চালু, মেনু বা সেটিংস থেকে এক ট্যাপে বন্ধ। |
| **🇧🇩 পুরো ইন্টারফেস বাংলায়** | মেনু, সেটিংস, বার্তা, হোম পেজ — সব বাংলায়। বয়স্ক বা ইংরেজিতে অনভ্যস্ত ব্যবহারকারীরাও চালাতে পারেন। |
| **🪶 মাত্র ১২৫ কিলোবাইট** | Chrome বা Firefox-এর চেয়ে বহুগুণ ছোট, তাই পুরোনো আর কম মেমরির ফোনেও দ্রুত চলে। ইনস্টল করতেও জায়গা লাগে না। |
| **📵 বিজ্ঞাপন নেই, ট্র্যাকিং নেই** | কোনো অ্যানালিটিক্স, কোনো ইন-অ্যাপ কেনাকাটা, কোনো অ্যাকাউন্ট লাগে না। |
| **🧩 প্রিয় সাইট এডিটর** | হোম পেজের সাইটের তালিকা নিজের মতো সাজানো যায়, প্রতিটার আসল আইকনসহ। |
| **🔓 MPL-2.0 ওপেন সোর্স** | পুরো কোড GitHub-এ। রিভিউ করা যায়, নিজে বিল্ড করা যায়। |

## Features

| | |
|---|---|
| **বাংলা ইন্টারফেস** | Every button, menu and message is in Bengali. |
| **Auto Bengali translation** | Any English page is translated into Bengali automatically, in place, as the text appears. On by default; one tap turns it off in the menu or in Settings. |
| **Tabs** | Up to 32 tabs with a Chrome style switcher and a tab counter in the toolbar. |
| **Edge style menu** | A bottom sheet with new tab, bookmarks, history, downloads, share, desktop site and dark mode. |
| **Search built in** | Type a word to search Google, DuckDuckGo or Bing; type an address to open the site. |
| **Bookmarks & history** | Saved locally; history opens full screen with the real site icon and a short name for every page. |
| **Dark mode** | Full dark theme, including the built-in home page. |
| **Downloads & uploads** | Built-in downloader with cookies and user agent, live progress (size, percent, speed, remaining) and a cancel button. File picker for uploads. |
| **Session restore** | Reopen the app and your tabs are still there. |
| **Favourite sites** | The home page grid is editable: add, remove and reorder sites, with their own icons. |
| **Settings** | Search engine, text size, JavaScript, images, link in new tab, default browser, clear history, bookmarks, cookies, cache and all data. |

## Install

1. Download **[BanglaBrowser-1.2.apk](https://github.com/Mamun015/bangla-browser/releases/latest)**.
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

Output: `BanglaBrowser-1.2.apk`. See [BUILD.md](BUILD.md) for details. The signing key (`keystore.jks`) is not in the repository; generate your own to build a fork.

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
