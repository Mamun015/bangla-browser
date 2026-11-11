# বাংলা ব্রাউজার (Bangla Browser)

A small, fast Android browser with a full Bengali interface. WebView based, no ads, no tracking, under 50 KB.

ছোট আর দ্রুত একটি অ্যান্ড্রয়েড ব্রাউজার, পুরোটাই বাংলায়। বিজ্ঞাপন নেই, ট্র্যাকিং নেই।

![screenshot](docs/screenshot.png)

**সরাসরি ডাউনলোড:** [BanglaBrowser-1.0.apk](https://github.com/mamun0937/bangla-browser/releases/latest) (৪৯ কিলোবাইট)

## ফিচার

- বাংলা ইন্টারফেস, উপরের ঠিকানা বার, নিচে পাঁচটি বাটন
- ঠিকানা লিখলে সাইট খোলে, বাংলা শব্দ লিখলে গুগলে সার্চ হয়
- একসাথে ৮টা পর্যন্ত ট্যাব, Chrome-এর মতো ট্যাব সুইচার
- Edge-স্টাইলের নিচের মেনু: নতুন ট্যাব, বুকমার্ক, ইতিহাস, ডাউনলোড, শেয়ার, ডেস্কটপ সাইট, রাতের মোড
- সেটিংস: সার্চ ইঞ্জিন (Google, DuckDuckGo, Bing), লেখার আকার, জাভাস্ক্রিপ্ট, ছবি লোড
- ডেটা মোছার সুবিধা: ইতিহাস, বুকমার্ক, কুকি ও ক্যাশে
- ফাইল ডাউনলোড ও আপলোড (ফেসবুকে ছবি দেওয়া যায়)
- কুকি সেভ থাকে, তাই সাইটে লগইন থাকে
- হোম পেজ: প্রথম আলো, বিডিনিউজ, উইকিপিডিয়া, ইউটিউব, ফেসবুকের শর্টকাট

## Features

- Bengali UI, address bar with search, five-button bottom nav
- Up to 8 tabs with a Chrome style tab switcher
- Edge style bottom menu: new tab, bookmarks, history, downloads, share, desktop site, dark mode
- Settings: search engine (Google, DuckDuckGo, Bing), text size, JavaScript, images
- Clear history, bookmarks, cookies and cache
- Download manager and file upload support
- Cookies persist, so logins stay
- Bundled home page with popular Bengali sites

## ইনস্টল

1. [Releases](../../releases) থেকে `BanglaBrowser-1.0.apk` ডাউনলোড করো।
2. ফাইল ম্যানেজার দিয়ে APK-তে ট্যাপ করো।
3. "Allow from this source" চালু করো, তারপর Install।

The APK is signed with a self-generated key, so Android will warn about an unknown source. That is expected for apps installed outside the Play Store.

## Build

No Gradle, no Android Studio. A shell script assembles, compiles, dexes, aligns and signs the APK. See [BUILD.md](BUILD.md).

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 bash build.sh
```

The whole thing builds on the phone itself: this project was written and compiled on an Android device.

## সীমাবদ্ধতা

- Google blocks sign-in inside WebView, so Gmail and YouTube logins open in Chrome instead.
- DRM video (Netflix, Prime Video) does not play.
- A few sites refuse to load in WebView.

## License

MIT. Do whatever you want with it.
