# Build Information

## Project Information
  - **Project Name:** Chessy
  - **Package Name:** com.mhrahmankwoshik.chessy

## Build Environment
  - **Android Studio / IntelliJ IDEA (Community Edition):** Ladybug / Iguana or later (IDEA 2024.3+)
  - **Gradle Version:** 8.12
  - **Kotlin Version:** 2.0.21
  - **Java Version:** 17 (Source & Target compatibility), JDK 21 (Build)

## SDK and Tools
  - **compileSdkVersion:** 34
  - **buildToolsVersion:** 34.0.0
  - **minSdkVersion:** 24
  - **targetSdkVersion:** 34

## Gradle
  - **AGP Version:** 8.7.3

## Dependencies
  - `androidx.appcompat:appcompat:1.7.0`
  - `com.google.android.material:material:1.12.0`
  - `androidx.activity:activity-ktx:1.9.3`
  - `androidx.constraintlayout:constraintlayout:2.2.0`
  - `androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7`
  - `androidx.lifecycle:lifecycle-livedata-ktx:2.8.7`
  - `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0`

## Build Status
  - **Success:** Yes

---

## Build Instructions

### Terminal

1. **Install prerequisites**
   - Java Development Kit (JDK 17 or 21)
   - Android SDK (API 34)

2. **Open the project in terminal**
   ```bash
   cd chessy
   ```

3. **Run unit tests**
   ```bash
   ./gradlew testDebugUnitTest
   ```

4. **Build Debug APK**
   ```bash
   ./gradlew assembleDebug
   ```
   The debug APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

5. **Build Release APK**
   ```bash
   ./gradlew assembleRelease
   ```
   The release APK will be generated at `app/build/outputs/apk/release/app-release-unsigned.apk`.

6. **Install via ADB**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
