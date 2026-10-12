# LMS Official Android Client (`com.exam.lms`)

Official native Android client for the LMS Exam Platform, engineered for 100% functional parity with the PHP web platform.

## Network & Mock Server Configuration

- **`BASE_URL`**: Configured in `app/src/main/java/com/exam/lms/di/NetworkModule.kt` as `https://lms.exam.com/api/v1/`.
- **Mock API Interceptor**: `MockApiInterceptor.kt` intercepts all OkHttp requests and supplies fake response payloads matching all 89 contract endpoints whenever `USE_MOCK_SERVER = true` (default). To connect to a live backend, set `USE_MOCK_SERVER = false` in `NetworkModule.kt`.

## Architectural Overview

- **36 Destination Screens**: Auth, Student, Faculty, and Admin flows fully implemented.
- **Jetpack Compose & Material 3**: Dynamic themes, Status Badges, Data Tables, QR Generators, Timer Countdowns, and Analytics charts.
- **Keystore TokenStore**: Encrypted token storage using Android Keystore (`EncryptedSharedPreferences`).
- **Single-Flight Token Refresh**: Retrofit `AuthInterceptor` using Coroutines `Mutex` to prevent concurrent refresh loops.
- **Business Parity Rules**:
  - Autosave queue with 2s debouncing and 60s per-question 429 rate limit throttling (§12).
  - Auto-submission on test timer expiry (T10).
  - Standard competition ranking (1, 1, 3) tie-breaker calculations (R4).
  - Anti-cheat lifecycle monitoring (`FLAG_SECURE` screen protection and tab-switch/pause detection C1).

## Build & Test Commands

### Environment Setup
- JDK 17+ (`C:\Program Files\Android\Android Studio\jbr`)
- Android SDK 35 / AGP 8.5.2

### Run Unit Tests
```cmd
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
cd /d C:\Users\B_Hari_Krishna_Kumar\AndroidStudioProjects\LMS
.\gradlew.bat testDebugUnitTest
```

### Assemble Debug APK
```cmd
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
cd /d C:\Users\B_Hari_Krishna_Kumar\AndroidStudioProjects\LMS
.\gradlew.bat assembleDebug
```
Output APK path: `app/build/outputs/apk/debug/app-debug.apk`

## Remote Git Repository
- **URL**: `https://github.com/HariKrishnaKumar/lms_android.git`
- **Branch**: `master`
