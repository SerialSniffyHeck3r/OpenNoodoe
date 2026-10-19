# 빌드와 설치

Android 6.0/API 23 이상, SDK 36, Java 17 또는 21, Gradle 8.7(래퍼 포함), Android Gradle Plugin 8.5.2를 사용합니다. `ANDROID_HOME`, `JAVA_HOME`을 설정하고 `android/`에서 실행합니다.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows는 `gradlew.bat`를 사용합니다. 첫 빌드에서는 Gradle과 의존성을 내려받습니다. 디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk`입니다. Android Studio에서 `android/`를 직접 열 수 있습니다. AGP 8.5.2의 compileSdk 36 지원 경고는 기존 도구 버전을 유지하기 때문에 발생합니다.

## 서명된 릴리즈

저장소 밖에 자신의 키스토어를 만들고 로컬 properties 파일을 작성합니다.

```properties
storeFile=/absolute/path/to/your-key.p12
storePassword=YOUR_PASSWORD
keyAlias=upload
keyPassword=YOUR_PASSWORD
```

이 파일과 키는 커밋하지 않습니다. `storeFile`은 properties 파일 기준 상대 경로도 가능합니다.

```sh
./gradlew -PopenNoodoeSigningProperties=/absolute/path/keystore.properties testReleaseUnitTest lintRelease assembleRelease bundleRelease
```

APK는 `app/build/outputs/apk/release/app-release.apk`, AAB는 `app/build/outputs/bundle/release/app-release.aab`입니다. AAB는 Play 업로드용이며 직접 설치하는 APK와 다릅니다. 릴리즈에는 서명이 필수이며 R8·리소스 축소를 적용하고 테마 에셋 번들을 거부합니다.

## 설치

Releases의 APK를 Android 설치 화면에서 열거나 `adb install -r OpenNoodoe-0.10.1.apk`로 설치합니다. 주변 기기 권한을 허용합니다. Android 11 이하에서는 Bluetooth 검색에 위치 권한을 사용합니다. 페어링된 계기판을 선택해 연결합니다. 공식 앱이 연결 중이면 먼저 연결을 종료합니다.

패키지 ID와 서명 인증서가 같아야 데이터를 유지하며 업데이트할 수 있습니다. 과거 개인 테스트 빌드는 다른 키를 사용했으므로 해당 빌드 위로의 업데이트는 Android가 거부합니다. 설치 식별자를 바꾸기 전 필요한 로컬 데이터를 내보내십시오.

배포 서명 인증서 SHA-256:
`d7b967bbf20a445c30c7852929880c9178c9e70f18e463944bbac32acd20b8d6`

다운로드 APK의 해시는 `SHA256SUMS.txt`와 대조하고 인증서는 Android SDK의 `apksigner verify --print-certs`로 확인할 수 있습니다.


[English](../en/quickstart.md) · [목차](../../README.ko.md)
