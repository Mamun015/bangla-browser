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

Output: `BanglaBrowser-1.0.apk`.

`build.sh` creates `keystore.jks` on the first run. Keep that file. Every future version must be signed with the same key, otherwise Android refuses to install the update over the old app.

## Layout

```
AndroidManifest.xml                     app identity: name, icon, permissions
assets/home.html                        start page shown when the app opens
src/bd/bangla/browser/MainActivity.java  the app: UI, WebView, bookmarks, history
res/mipmap-*/ic_launcher.png            launcher icon in five densities
tools/gen_icon.py                       draws the icon icons from scratch (stdlib only)
build.sh                                the build
```

## Changelog

- 1.0 first build: browser, bookmarks, history, dark mode, downloads, uploads.
