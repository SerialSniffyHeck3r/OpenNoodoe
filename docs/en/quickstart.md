# Build and install

Android 6.0/API 23 or later; SDK 36; Java 17 or 21; Gradle 8.7 (wrapper included); Android Gradle Plugin 8.5.2. Set `ANDROID_HOME` and `JAVA_HOME`, then run from `android/`:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

On Windows use `gradlew.bat`. The first build downloads Gradle and dependencies. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Android Studio can open `android/` directly. AGP 8.5.2 reports a compileSdk 36 support warning; this release preserves the existing toolchain.

## Signed release

Create your own keystore outside the repository, then a local properties file:

```properties
storeFile=/absolute/path/to/your-key.p12
storePassword=YOUR_PASSWORD
keyAlias=upload
keyPassword=YOUR_PASSWORD
```

Do not commit this file or key. `storeFile` can also be relative to the properties file.

```sh
./gradlew -PopenNoodoeSigningProperties=/absolute/path/keystore.properties testReleaseUnitTest lintRelease assembleRelease bundleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`. AAB: `app/build/outputs/bundle/release/app-release.aab`. AAB is a Play upload artifact, not an installable APK. Release builds require explicit signing, enable R8/resource shrinking and reject bundled theme assets.

## Install

Install the Releases APK with Android's package installer or `adb install -r OpenNoodoe-0.10.1.apk`. Grant Nearby devices; Android 11 and earlier use location permission for Bluetooth discovery. Select the paired dashboard and connect. Close the official app's connection first.

Updates preserve app data only when package ID and signing certificate match. Earlier personal-test builds used another key; Android rejects an in-place update from those builds. Export wanted local data before changing installation identity.

Published signing certificate SHA-256:
`d7b967bbf20a445c30c7852929880c9178c9e70f18e463944bbac32acd20b8d6`

Check the downloaded APK against `SHA256SUMS.txt`, and inspect its certificate with Android SDK `apksigner verify --print-certs`.


[한국어](../ko/quickstart.md) · [Index](../../README.md)
