# Building

The project builds without Gradle or Android Studio. `build.sh` runs six steps: package resources with `aapt`, compile Java with `javac`, convert bytecode to `classes.dex` with `d8`, add the dex to the APK, align it with `zipalign`, and sign it with `apksigner`.

## Requirements

- JDK 17
- `aapt`, `zipalign`, `apksigner` (Debian/Ubuntu: `apt install aapt zipalign apksigner`)
- `android.jar` from the Android 13 platform (API 33)
- `r8.jar` from Google Maven

On Debian or Ubuntu:

```bash
apt install openjdk-17-jdk-headless aapt zipalign apksigner curl unzip zip
mkdir -p sdk
curl -o /tmp/p.zip https://dl.google.com/android/repository/platform-33_r02.zip
unzip -j /tmp/p.zip "android-*/android.jar" -d sdk
curl -o sdk/r8.jar https://maven.google.com/com/android/tools/r8/8.3.37/r8-8.3.37.jar
```

## Build

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64 bash build.sh
```

Output: `BanglaBrowser-2.0.1.apk`.

## Signing

Release APKs are signed by `sign.sh` with a two-key chain kept outside the
repository: the previous key (v1/v2 signatures, Android 5–8), the current key
(v3 signature, Android 9+) and the lineage that links them. Both keys are
required on every build.

Point `SKYBLE_SIGNING_DIR` at the directory that holds the chain (default
`$HOME/Keystores/skyble`). Passwords and aliases come from `keystore.properties`
and `skyble-release.properties` there, or from the matching `SKYBLE_*`
environment variables.

Build with `UNSIGNED=1` when the chain is not available. Never publish an APK
signed outside the chain: existing installs reject a different signature.

## Layout

```
AndroidManifest.xml                     app identity: name, icon, permissions
assets/home.html                        start page shown when the app opens
src/bd/bangla/browser/MainActivity.java  the app: UI, WebView, bookmarks, history
res/mipmap-*/ic_launcher.png            launcher icon in five densities
tools/gen_icon.py                       draws the launcher icons from scratch (stdlib only)
build.sh                                the build
```

## Changelog

- 1.1.03 default browser option, YouTube fullscreen video, in-app downloads list, branded bottom menu.
- 1.1.02 move the project to MPL-2.0, show the license in Settings, no feature changes.
- 1.1.01 fix blank menu actions (settings and about now open), About page with maker details and "Made in Bangladesh", bigger colorful launcher icon, nicer menu icons, lazy tab loading, sheet height cap, main-frame only error page.
- 1.1 tabs with a Chrome style switcher, Edge style bottom menu, settings screen (search engine, text size, JavaScript, images, clear data), session restore.
- 1.0 first build: browser, bookmarks, history, dark mode, downloads, uploads.


## Website

The landing page lives in `docs/` and is published at
<https://mamun015.github.io/bangla-browser/> from the `gh-pages` branch.
After editing anything in `docs/`, copy `index.html`, `icon.png`,
`screenshot.png`, `robots.txt` and `sitemap.xml` to the root of `gh-pages`
and push, then request a rebuild in the repository's Pages settings.
