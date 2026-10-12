# LMS Official Android Client (`com.exam.lms`)

Official native Android client for the LMS Exam Platform, engineered for 100% functional parity with the PHP web platform.

## Features & Architecture

- **36 Destination Screens**: Auth, Student, Faculty, and Admin flows fully implemented.
- **Jetpack Compose & Material 3**: UI design system with dynamic themes, Status Badges, Data Tables, QR Generators, Timer Countdowns, and Analytics charts.
- **Keystore TokenStore**: Encrypted token storage leveraging Android Keystore (`EncryptedSharedPreferences`).
- **Single-Flight Token Refresh**: Retrofit `AuthInterceptor` using Coroutines `Mutex` to eliminate concurrent token refresh race conditions.
- **Mock Server Layer**: Integrated OkHttp `MockApiInterceptor` providing full mock data for offline simulation and testing.
- **Business Parity Rules**:
  - Autosave queue with 2s debouncing and 60s per-question 429 rate limit throttling (§12).
  - Auto-submission on test timer expiry (T10).
  - Standard competition ranking (1, 1, 3) tie-breaker calculations (R4).
  - Anti-cheat lifecycle monitoring (`FLAG_SECURE` screen protection and tab-switch/pause detection C1).

## Build & Test Instructions

### Environment
- JDK 17+ (`C:\Program Files\Android\Android Studio\jbr`)
- Android SDK 35 / AGP 8.5.2

### Run Unit Tests
```cmd
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
.\gradlew.bat testDebugUnitTest
```

### Build Debug APK
```cmd
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
.\gradlew.bat assembleDebug
```
Output APK location: `app/build/outputs/apk/debug/app-debug.apk`

## Remote Repository
- **URL**: `https://github.com/HariKrishnaKumar/lms_android.git`
