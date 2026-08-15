# 홈/자동차 소모품 정비 노트 앱 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** golf-round-tracker와 동일한 스택(Kotlin/Compose/Room/Material3)으로, 가전·자동차 소모품의 교체 주기를 관리하고 D-day 알림과 교체/구매 이력을 기록하는 Android 앱(`k-home-note`)을 처음부터 만든다.

**Architecture:** Asset(자산) 1:N ConsumableItem(소모품) 1:N ReplacementRecord(교체이력) 3단 Room 데이터 모델. 다음 교체일 계산과 알림 대상 판정은 Android 의존성이 없는 순수 함수(`domain/`)로 분리해 TDD한다. 화면 전환은 golf-round-tracker와 동일하게 별도 Navigation 라이브러리 없이 `sealed interface Screen` + `mutableStateListOf` 수동 백스택으로 처리한다(자세한 이유는 "설계 노트" 참고). DI는 수동 `AppContainer` 싱글턴.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Room+KSP, WorkManager, Coil3(로컬 사진 썸네일), JUnit4.

## Global Constraints

- minSdk 26, compileSdk/targetSdk 36 (spec: 기술 스택)
- 패키지명 `com.khomenote`, 프로젝트 루트 `C:\github\k-home-note` (spec: 프로젝트)
- 클라우드 동기화/백업 없음, 기기 내 Room DB만 사용 (spec: 범위)
- 교체 주기는 날짜(day) 기준으로만 계산 — 주행거리(km) 등 사용량 기준 없음 (spec: 비범위)
- 알림은 WorkManager 하루 1회 스캔 방식만 사용, AlarmManager 정확 알람/관련 권한 처리 없음 (spec: 알림)
- 사진은 앱 내부 저장소(`filesDir/photos/`)로 복사해 경로를 저장 (spec: 기술 스택)
- Android 앱 코드가 바뀌는 커밋마다 `versionName`의 PATCH를 올리고 커밋 제목 끝에 `(vX.Y.Z)`를 붙인다 (워크스페이스 규칙, `C:\github\CLAUDE.md`). `versionCode`는 이 계획 범위에서 올리지 않는다(태그 시점 규칙).
- 화면 렌더링/동작 확인은 adb로 tap을 넘겨가며 직접 확인하지 않는다 — 실기기 설치 후 "무엇을 탭해서 무엇을 확인해달라"고 사용자에게 구체적으로 요청한다 (워크스페이스 규칙). DB 값 확인은 `adb shell run-as com.khomenote cat ...`으로 직접 조회해도 된다.
- golf-round-tracker(`C:\github\golf-round-tracker`)의 기존 컨벤션을 그대로 따른다: `src/main/kotlin` 소스셋, `data/local/entity|dao|dto`, `data/repository`, `domain`, `ui/<feature>`(ViewModel+Factory를 Screen과 같은 파일에 co-locate), `di/AppContainer` 수동 DI.

## 설계 노트 (구현 계획 작성 중 확정한 결정)

- **Navigation 라이브러리 대신 수동 백스택**: golf-round-tracker를 조사한 결과 `androidx.navigation:navigation-compose`를 쓰지 않고 `sealed interface Screen` + `mutableStateListOf<Screen>` + `when` 분기로 직접 화면을 전환하고 있었다(`MainActivity.kt`, `ui/navigation/Screen.kt`). 이 앱도 화면이 6개뿐이라 별도 라이브러리를 추가할 이유가 없어(YAGNI) 동일 패턴을 따른다 — 브레인스토밍 단계에서 계획된 `navigation-compose` 의존성은 이 이유로 빼고, 대신 golf-round-tracker와 동일한 수동 백스택 패턴을 쓴다.
- **날짜 타입은 `LocalDate`**: golf-round-tracker는 날짜를 `Long`(epoch millis)으로 저장하지만, 이 앱은 "교체일 + N일" 같은 일(day) 단위 계산이 핵심이라 타임존/월 길이 문제 없이 정확한 `LocalDate.plusDays()`를 쓰는 게 맞다고 판단해 `LocalDate` + Room `TypeConverters`(epochDay Long 변환)를 쓰기로 결정했다. minSdk 26이라 `java.time` API가 desugaring 없이 그대로 동작한다.
- **사진 썸네일에 Coil3 추가**: 설계 문서에 "영수증/제품 사진"이 이력 필수 항목으로 들어가 있는데, 저장만 하고 화면에서 전혀 볼 방법이 없으면 기능이 반쪽이 된다. `FileProvider` + 뷰어를 새로 만드는 대신, 로컬 파일을 바로 그려주는 `io.coil-kt.coil3:coil-compose:3.5.0` 하나만 추가해 상세 화면에 작은 썸네일로 보여준다(전체화면 뷰어는 만들지 않음 — YAGNI).
- **연체 알림은 하루만 더 울림**: `ReminderTargetSelector`는 "D-reminderLeadDays 되는 날"과 "D-day 당일" 딱 두 시점에만 알림을 낸다. D-day를 지나 계속 연체 중인 항목을 매일 다시 알리지는 않는다(스팸 방지) — 대신 홈 대시보드에 연체 항목이 빨간색으로 계속 상단에 남아있어서 놓치지 않는다.

---

## Task 1: 프로젝트 scaffolding

**Files:**
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (golf-round-tracker에서 복사)
- Create: `gradle/libs.versions.toml`
- Create: `build.gradle.kts` (root)
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `.gitignore`
- Create: `local.properties` (커밋 안 함)
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/khomenote/KHomeNoteApp.kt`
- Create: `app/src/main/kotlin/com/khomenote/MainActivity.kt` (Task 10에서 전체 교체)
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/drawable/ic_launcher_background.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

**Interfaces:**
- Produces: 빌드 가능한 빈 앱 셸(`KHomeNoteApp`, `MainActivity`가 "K-Home-Note" 텍스트만 표시). 이후 모든 태스크가 이 Gradle 구성 위에서 파일을 추가한다.

- [ ] **Step 1: Gradle wrapper 파일 복사**

```bash
mkdir -p /c/github/k-home-note/gradle/wrapper
cp /c/github/golf-round-tracker/gradle/wrapper/gradle-wrapper.jar /c/github/k-home-note/gradle/wrapper/gradle-wrapper.jar
cp /c/github/golf-round-tracker/gradle/wrapper/gradle-wrapper.properties /c/github/k-home-note/gradle/wrapper/gradle-wrapper.properties
cp /c/github/golf-round-tracker/gradlew /c/github/k-home-note/gradlew
cp /c/github/golf-round-tracker/gradlew.bat /c/github/k-home-note/gradlew.bat
chmod +x /c/github/k-home-note/gradlew
```

- [ ] **Step 2: 버전 카탈로그 작성**

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.0.1"
kotlin = "2.2.10"
ksp = "2.2.10-2.0.2"
composeBom = "2026.03.01"
activityCompose = "1.13.0"
coreKtx = "1.15.0"
lifecycleRuntimeKtx = "2.9.0"
junit = "4.13.2"
room = "2.7.0"
coroutines = "1.10.2"
lifecycleViewmodelCompose = "2.10.0"
work = "2.11.2"
coil = "3.5.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 3: 루트 Gradle 파일 작성**

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "k-home-note-android"
include(":app")
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.java.home=C:\\Program Files\\Microsoft\\jdk-17.0.19.10-hotspot
android.useAndroidX=true
android.nonTransitiveRClass=true
android.disallowKotlinSourceSets=false
```

`.gitignore`:

```
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
/build/
/captures/
.externalNativeBuild/
.cxx/
app/build/
app/schemas/
```

`local.properties` (커밋하지 않음, `.gitignore`의 `/local.properties`로 이미 제외됨):

```properties
sdk.dir=C\:\\Users\\dayfl\\AppData\\Local\\Android\\Sdk
```

- [ ] **Step 4: 앱 모듈 Gradle 파일 작성**

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.khomenote"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.khomenote"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
}
```

- [ ] **Step 5: Manifest, 리소스, 런처 아이콘 작성**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".KHomeNoteApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:theme="@android:style/Theme.Material.Light.NoActionBar"
        android:supportsRtl="true">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:theme="@android:style/Theme.Material.Light.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">K-Home-Note</string>
</resources>
```

`app/src/main/res/drawable/ic_launcher_background.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#00695C"
        android:pathData="M0,0h108v108h-108z" />
</vector>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <!-- 체크마크 -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M34,58 L48,72 L82,36" />
    <!-- K 모노그램 (golf-round-tracker/yt-sub-digest와 동일 스타일) -->
    <path
        android:fillColor="#00000000"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="3"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="M30,80L30,92M30,86L40,80M30,86L40,92" />
</vector>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`: 위 파일과 동일한 내용으로 생성.

- [ ] **Step 6: 앱 셸 Kotlin 파일 작성**

`app/src/main/kotlin/com/khomenote/KHomeNoteApp.kt`:

```kotlin
package com.khomenote

import android.app.Application

class KHomeNoteApp : Application()
```

`app/src/main/kotlin/com/khomenote/MainActivity.kt`:

```kotlin
package com.khomenote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("K-Home-Note")
                }
            }
        }
    }
}
```

- [ ] **Step 7: 빌드 확인**

Run: `cd /c/github/k-home-note && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. 실패하면 Gradle/AGP/Kotlin 버전 조합 문제이니 golf-round-tracker가 실제로 이 조합으로 빌드되는지(`cd /c/github/golf-round-tracker && ./gradlew assembleDebug`) 먼저 확인해 차이를 찾는다.

- [ ] **Step 8: 실기기 설치 후 사용자 확인**

Run: `adb install -r /c/github/k-home-note/app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n com.khomenote/.MainActivity`

사용자에게 요청: "홈 화면에 새로 생긴 체크마크 아이콘의 앱(K-Home-Note)을 열어서 화면에 'K-Home-Note'라는 텍스트가 보이는지 확인해주세요."

- [ ] **Step 9: 커밋**

```bash
cd /c/github/k-home-note
git add gradlew gradlew.bat gradle/ build.gradle.kts settings.gradle.kts gradle.properties .gitignore app/build.gradle.kts app/src
git commit -m "$(cat <<'EOF'
chore: k-home-note 프로젝트 scaffolding (v0.1.0)

golf-round-tracker와 동일한 Gradle/Compose/Room 구성으로 빈 앱 셸을 만든다.
EOF
)"
```

---

## Task 2: DueDateCalculator (다음 교체 예정일 계산, TDD)

**Files:**
- Create: `app/src/main/kotlin/com/khomenote/domain/DueDateCalculator.kt`
- Test: `app/src/test/kotlin/com/khomenote/domain/DueDateCalculatorTest.kt`

**Interfaces:**
- Produces: `DueDateCalculator.nextDueDate(itemCreatedAt: LocalDate, cycleDays: Int, latestRecordDate: LocalDate?): LocalDate`, `DueDateCalculator.daysUntilDue(nextDueDate: LocalDate, today: LocalDate): Long`. Task 3(ReminderTargetSelector), Task 4(DAO 쿼리 결과 가공), Task 8/9(화면)에서 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/kotlin/com/khomenote/domain/DueDateCalculatorTest.kt`:

```kotlin
package com.khomenote.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DueDateCalculatorTest {

    @Test
    fun `nextDueDate uses latest record date plus cycle when a record exists`() {
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2026, 1, 1),
            cycleDays = 180,
            latestRecordDate = LocalDate.of(2026, 6, 1),
        )
        assertEquals(LocalDate.of(2026, 11, 28), result)
    }

    @Test
    fun `nextDueDate falls back to item createdAt when there is no record yet`() {
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2026, 1, 1),
            cycleDays = 30,
            latestRecordDate = null,
        )
        assertEquals(LocalDate.of(2026, 1, 31), result)
    }

    @Test
    fun `daysUntilDue is positive when due date is in the future`() {
        val result = DueDateCalculator.daysUntilDue(
            nextDueDate = LocalDate.of(2026, 8, 20),
            today = LocalDate.of(2026, 8, 15),
        )
        assertEquals(5L, result)
    }

    @Test
    fun `daysUntilDue is negative when due date already passed`() {
        val result = DueDateCalculator.daysUntilDue(
            nextDueDate = LocalDate.of(2026, 8, 10),
            today = LocalDate.of(2026, 8, 15),
        )
        assertEquals(-5L, result)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /c/github/k-home-note && ./gradlew test --tests "com.khomenote.domain.DueDateCalculatorTest"`
Expected: FAIL (컴파일 에러 — `DueDateCalculator`가 아직 없음)

- [ ] **Step 3: 구현**

`app/src/main/kotlin/com/khomenote/domain/DueDateCalculator.kt`:

```kotlin
package com.khomenote.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DueDateCalculator {
    fun nextDueDate(itemCreatedAt: LocalDate, cycleDays: Int, latestRecordDate: LocalDate?): LocalDate =
        (latestRecordDate ?: itemCreatedAt).plusDays(cycleDays.toLong())

    fun daysUntilDue(nextDueDate: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, nextDueDate)
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /c/github/k-home-note && ./gradlew test --tests "com.khomenote.domain.DueDateCalculatorTest"`
Expected: PASS (테스트 4개 모두 통과)

- [ ] **Step 5: 버전 올리고 커밋**

`app/build.gradle.kts`의 `versionName`을 `"0.1.0"` → `"0.1.1"`로 수정.

```bash
cd /c/github/k-home-note
git add app/build.gradle.kts app/src/main/kotlin/com/khomenote/domain/DueDateCalculator.kt app/src/test/kotlin/com/khomenote/domain/DueDateCalculatorTest.kt
git commit -m "$(cat <<'EOF'
feat: 다음 교체 예정일 계산 로직 추가 (v0.1.1)

Android/Room 의존성 없는 순수 함수로 분리해 TDD로 검증.
EOF
)"
```

---

## Task 3: ReminderTargetSelector (알림 대상 판정, TDD)

**Files:**
- Create: `app/src/main/kotlin/com/khomenote/domain/ReminderTargetSelector.kt`
- Test: `app/src/test/kotlin/com/khomenote/domain/ReminderTargetSelectorTest.kt`

**Interfaces:**
- Consumes: `DueDateCalculator.daysUntilDue` (Task 2)
- Produces: `data class ReminderCandidate(consumableItemId: Long, nextDueDate: LocalDate, reminderLeadDays: Int)`, `ReminderTargetSelector.selectTargets(candidates: List<ReminderCandidate>, today: LocalDate): List<ReminderCandidate>`. Task 6(ReminderWorker)에서 사용.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/kotlin/com/khomenote/domain/ReminderTargetSelectorTest.kt`:

```kotlin
package com.khomenote.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReminderTargetSelectorTest {

    private val today = LocalDate.of(2026, 8, 15)

    @Test
    fun `selects item exactly at its reminder lead time`() {
        val candidate = ReminderCandidate(
            consumableItemId = 1L,
            nextDueDate = LocalDate.of(2026, 8, 22),
            reminderLeadDays = 7,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(listOf(candidate), result)
    }

    @Test
    fun `selects item due exactly today regardless of lead days`() {
        val candidate = ReminderCandidate(
            consumableItemId = 2L,
            nextDueDate = LocalDate.of(2026, 8, 15),
            reminderLeadDays = 3,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(listOf(candidate), result)
    }

    @Test
    fun `excludes item that is neither at lead time nor due today`() {
        val candidate = ReminderCandidate(
            consumableItemId = 3L,
            nextDueDate = LocalDate.of(2026, 9, 1),
            reminderLeadDays = 7,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(emptyList<ReminderCandidate>(), result)
    }

    @Test
    fun `does not re-notify for an item that is already overdue by more than the lead time`() {
        val candidate = ReminderCandidate(
            consumableItemId = 4L,
            nextDueDate = LocalDate.of(2026, 8, 1),
            reminderLeadDays = 7,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(emptyList<ReminderCandidate>(), result)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /c/github/k-home-note && ./gradlew test --tests "com.khomenote.domain.ReminderTargetSelectorTest"`
Expected: FAIL (컴파일 에러 — `ReminderCandidate`/`ReminderTargetSelector`가 아직 없음)

- [ ] **Step 3: 구현**

`app/src/main/kotlin/com/khomenote/domain/ReminderTargetSelector.kt`:

```kotlin
package com.khomenote.domain

import java.time.LocalDate

data class ReminderCandidate(
    val consumableItemId: Long,
    val nextDueDate: LocalDate,
    val reminderLeadDays: Int,
)

object ReminderTargetSelector {
    fun selectTargets(candidates: List<ReminderCandidate>, today: LocalDate): List<ReminderCandidate> =
        candidates.filter { candidate ->
            val daysLeft = DueDateCalculator.daysUntilDue(candidate.nextDueDate, today)
            daysLeft == candidate.reminderLeadDays.toLong() || daysLeft == 0L
        }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /c/github/k-home-note && ./gradlew test --tests "com.khomenote.domain.ReminderTargetSelectorTest"`
Expected: PASS (테스트 4개 모두 통과)

- [ ] **Step 5: 버전 올리고 커밋**

`app/build.gradle.kts`의 `versionName`을 `"0.1.1"` → `"0.1.2"`로 수정.

```bash
cd /c/github/k-home-note
git add app/build.gradle.kts app/src/main/kotlin/com/khomenote/domain/ReminderTargetSelector.kt app/src/test/kotlin/com/khomenote/domain/ReminderTargetSelectorTest.kt
git commit -m "$(cat <<'EOF'
feat: 알림 대상 판정 로직 추가 (v0.1.2)

D-reminderLeadDays 시점과 D-day 당일 딱 두 번만 알림을 내도록 하고,
연체가 계속되는 항목을 매일 재알림하지 않도록 순수 함수로 검증.
EOF
)"
```

---

## Task 4: Room 데이터 계층 (엔티티/DAO/DB/리포지토리/DI)

**Files:**
- Create: `app/src/main/kotlin/com/khomenote/data/local/entity/AssetCategory.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/local/entity/AssetEntity.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/local/entity/ConsumableItemEntity.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/local/entity/ReplacementRecordEntity.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/local/Converters.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/local/dto/ConsumableItemWithStatus.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/local/dao/AssetDao.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/local/dao/ConsumableItemDao.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/local/dao/ReplacementRecordDao.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/local/AppDatabase.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/repository/AssetRepository.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/repository/ConsumableItemRepository.kt`
- Create: `app/src/main/kotlin/com/khomenote/data/repository/ReplacementRecordRepository.kt`
- Create: `app/src/main/kotlin/com/khomenote/di/AppContainer.kt`

**Interfaces:**
- Consumes: 없음 (Task 1의 앱 셸 위에 얹는다)
- Produces: `AppContainer.getInstance(context)` → `.assetRepository`, `.consumableItemRepository`, `.replacementRecordRepository` (Task 5에서 `.photoStorage`, Task 6에서 `.replacementRecordRepository` 재사용 추가 예정). `ConsumableItemWithStatus(item: ConsumableItemEntity, assetName: String, assetCategory: AssetCategory, latestRecordDate: LocalDate?)`는 Task 6, 8, 9에서 사용.

- [ ] **Step 1: 엔티티 작성**

`app/src/main/kotlin/com/khomenote/data/local/entity/AssetCategory.kt`:

```kotlin
package com.khomenote.data.local.entity

enum class AssetCategory { HOME, CAR }
```

`app/src/main/kotlin/com/khomenote/data/local/entity/AssetEntity.kt`:

```kotlin
package com.khomenote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: AssetCategory,
    val memo: String? = null,
    val createdAt: LocalDate = LocalDate.now(),
)
```

`app/src/main/kotlin/com/khomenote/data/local/entity/ConsumableItemEntity.kt`:

```kotlin
package com.khomenote.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "consumable_items",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("assetId")],
)
data class ConsumableItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val name: String,
    val cycleDays: Int,
    val reminderLeadDays: Int = 7,
    val memo: String? = null,
    val createdAt: LocalDate = LocalDate.now(),
)
```

`app/src/main/kotlin/com/khomenote/data/local/entity/ReplacementRecordEntity.kt`:

```kotlin
package com.khomenote.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "replacement_records",
    foreignKeys = [
        ForeignKey(
            entity = ConsumableItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["consumableItemId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("consumableItemId")],
)
data class ReplacementRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val consumableItemId: Long,
    val date: LocalDate,
    val price: Long? = null,
    val vendor: String? = null,
    val photoUri: String? = null,
    val memo: String? = null,
)
```

- [ ] **Step 2: LocalDate/enum TypeConverter 작성**

`app/src/main/kotlin/com/khomenote/data/local/Converters.kt`:

```kotlin
package com.khomenote.data.local

import androidx.room.TypeConverter
import com.khomenote.data.local.entity.AssetCategory
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromEpochDay(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun fromCategoryName(name: String?): AssetCategory? = name?.let(AssetCategory::valueOf)

    @TypeConverter
    fun toCategoryName(category: AssetCategory?): String? = category?.name
}
```

- [ ] **Step 3: 대시보드/상세용 조인 DTO 작성**

`app/src/main/kotlin/com/khomenote/data/local/dto/ConsumableItemWithStatus.kt`:

```kotlin
package com.khomenote.data.local.dto

import androidx.room.Embedded
import com.khomenote.data.local.entity.AssetCategory
import com.khomenote.data.local.entity.ConsumableItemEntity
import java.time.LocalDate

data class ConsumableItemWithStatus(
    @Embedded val item: ConsumableItemEntity,
    val assetName: String,
    val assetCategory: AssetCategory,
    val latestRecordDate: LocalDate?,
)
```

- [ ] **Step 4: DAO 작성**

`app/src/main/kotlin/com/khomenote/data/local/dao/AssetDao.kt`:

```kotlin
package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.khomenote.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Insert
    suspend fun insert(asset: AssetEntity): Long

    @Update
    suspend fun update(asset: AssetEntity)

    @Delete
    suspend fun delete(asset: AssetEntity)

    @Query("SELECT * FROM assets ORDER BY createdAt DESC")
    fun getAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :assetId")
    fun getById(assetId: Long): Flow<AssetEntity?>
}
```

`app/src/main/kotlin/com/khomenote/data/local/dao/ConsumableItemDao.kt`:

```kotlin
package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.ConsumableItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsumableItemDao {
    @Insert
    suspend fun insert(item: ConsumableItemEntity): Long

    @Update
    suspend fun update(item: ConsumableItemEntity)

    @Delete
    suspend fun delete(item: ConsumableItemEntity)

    @Query("SELECT * FROM consumable_items WHERE id = :itemId")
    fun getById(itemId: Long): Flow<ConsumableItemEntity?>

    @Query("SELECT * FROM consumable_items WHERE assetId = :assetId ORDER BY name ASC")
    fun getByAsset(assetId: Long): Flow<List<ConsumableItemEntity>>

    @Query(
        """
        SELECT
            ci.*,
            a.name AS assetName,
            a.category AS assetCategory,
            MAX(rr.date) AS latestRecordDate
        FROM consumable_items ci
        INNER JOIN assets a ON a.id = ci.assetId
        LEFT JOIN replacement_records rr ON rr.consumableItemId = ci.id
        GROUP BY ci.id
        ORDER BY ci.name ASC
        """
    )
    fun getAllWithStatus(): Flow<List<ConsumableItemWithStatus>>

    @Query(
        """
        SELECT
            ci.*,
            a.name AS assetName,
            a.category AS assetCategory,
            MAX(rr.date) AS latestRecordDate
        FROM consumable_items ci
        INNER JOIN assets a ON a.id = ci.assetId
        LEFT JOIN replacement_records rr ON rr.consumableItemId = ci.id
        WHERE ci.id = :itemId
        GROUP BY ci.id
        """
    )
    fun getWithStatusById(itemId: Long): Flow<ConsumableItemWithStatus?>
}
```

`app/src/main/kotlin/com/khomenote/data/local/dao/ReplacementRecordDao.kt`:

```kotlin
package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.khomenote.data.local.entity.ReplacementRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplacementRecordDao {
    @Insert
    suspend fun insert(record: ReplacementRecordEntity): Long

    @Delete
    suspend fun delete(record: ReplacementRecordEntity)

    @Query("SELECT * FROM replacement_records WHERE consumableItemId = :itemId ORDER BY date DESC")
    fun getByItem(itemId: Long): Flow<List<ReplacementRecordEntity>>
}
```

- [ ] **Step 5: AppDatabase 작성**

`app/src/main/kotlin/com/khomenote/data/local/AppDatabase.kt`:

```kotlin
package com.khomenote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.khomenote.data.local.dao.AssetDao
import com.khomenote.data.local.dao.ConsumableItemDao
import com.khomenote.data.local.dao.ReplacementRecordDao
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.ReplacementRecordEntity

@Database(
    entities = [AssetEntity::class, ConsumableItemEntity::class, ReplacementRecordEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun consumableItemDao(): ConsumableItemDao
    abstract fun replacementRecordDao(): ReplacementRecordDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "k_home_note.db")
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
```

`fallbackToDestructiveMigration`은 아직 실기기에 데이터가 쌓이기 전인 버전 1 단계에서만 쓰는 것이다 — golf-round-tracker의 `CLAUDE.md`가 지적하듯, 실제 사용자 데이터가 쌓이기 시작하면(버전 2 이상으로 스키마를 바꿔야 할 때) 이 프로젝트에도 명시적 `Migration` 객체로 바꿔야 한다. 이번 계획 범위 밖.

- [ ] **Step 6: 리포지토리 작성**

`app/src/main/kotlin/com/khomenote/data/repository/AssetRepository.kt`:

```kotlin
package com.khomenote.data.repository

import com.khomenote.data.local.dao.AssetDao
import com.khomenote.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

class AssetRepository(private val assetDao: AssetDao) {
    fun getAll(): Flow<List<AssetEntity>> = assetDao.getAll()
    fun getById(assetId: Long): Flow<AssetEntity?> = assetDao.getById(assetId)

    suspend fun save(asset: AssetEntity): Long =
        if (asset.id == 0L) assetDao.insert(asset) else { assetDao.update(asset); asset.id }

    suspend fun delete(asset: AssetEntity) = assetDao.delete(asset)
}
```

`app/src/main/kotlin/com/khomenote/data/repository/ConsumableItemRepository.kt`:

```kotlin
package com.khomenote.data.repository

import com.khomenote.data.local.dao.ConsumableItemDao
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.ConsumableItemEntity
import kotlinx.coroutines.flow.Flow

class ConsumableItemRepository(private val consumableItemDao: ConsumableItemDao) {
    fun getById(itemId: Long): Flow<ConsumableItemEntity?> = consumableItemDao.getById(itemId)
    fun getByAsset(assetId: Long): Flow<List<ConsumableItemEntity>> = consumableItemDao.getByAsset(assetId)
    fun getAllWithStatus(): Flow<List<ConsumableItemWithStatus>> = consumableItemDao.getAllWithStatus()
    fun getWithStatusById(itemId: Long): Flow<ConsumableItemWithStatus?> = consumableItemDao.getWithStatusById(itemId)

    suspend fun save(item: ConsumableItemEntity): Long =
        if (item.id == 0L) consumableItemDao.insert(item) else { consumableItemDao.update(item); item.id }

    suspend fun delete(item: ConsumableItemEntity) = consumableItemDao.delete(item)
}
```

`app/src/main/kotlin/com/khomenote/data/repository/ReplacementRecordRepository.kt`:

```kotlin
package com.khomenote.data.repository

import com.khomenote.data.local.dao.ReplacementRecordDao
import com.khomenote.data.local.entity.ReplacementRecordEntity
import kotlinx.coroutines.flow.Flow

class ReplacementRecordRepository(private val replacementRecordDao: ReplacementRecordDao) {
    fun getByItem(itemId: Long): Flow<List<ReplacementRecordEntity>> = replacementRecordDao.getByItem(itemId)
    suspend fun add(record: ReplacementRecordEntity): Long = replacementRecordDao.insert(record)
    suspend fun delete(record: ReplacementRecordEntity) = replacementRecordDao.delete(record)
}
```

- [ ] **Step 7: AppContainer 작성 (수동 DI)**

`app/src/main/kotlin/com/khomenote/di/AppContainer.kt`:

```kotlin
package com.khomenote.di

import android.content.Context
import com.khomenote.data.local.AppDatabase
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.ReplacementRecordRepository

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.getInstance(context)
    val assetRepository: AssetRepository = AssetRepository(database.assetDao())
    val consumableItemRepository: ConsumableItemRepository = ConsumableItemRepository(database.consumableItemDao())
    val replacementRecordRepository: ReplacementRecordRepository = ReplacementRecordRepository(database.replacementRecordDao())

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
```

- [ ] **Step 8: 빌드 확인**

Run: `cd /c/github/k-home-note && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (Room이 `app/schemas/com.khomenote.data.local.AppDatabase/1.json`을 생성한다 — `.gitignore`에 이미 포함되어 커밋 안 됨)

- [ ] **Step 9: 버전 올리고 커밋**

`app/build.gradle.kts`의 `versionName`을 `"0.1.2"` → `"0.1.3"`로 수정.

```bash
cd /c/github/k-home-note
git add app/build.gradle.kts app/src/main/kotlin/com/khomenote/data app/src/main/kotlin/com/khomenote/di
git commit -m "$(cat <<'EOF'
feat: Room 데이터 계층 추가 (v0.1.3)

Asset-ConsumableItem-ReplacementRecord 3단 구조와 대시보드/상세 화면이
쓸 조인 쿼리(ConsumableItemWithStatus)를 포함한 DAO/리포지토리/DI 컨테이너.
EOF
)"
```

---

## Task 5: 사진 저장 헬퍼

**Files:**
- Create: `app/src/main/kotlin/com/khomenote/photo/PhotoStorage.kt`
- Modify: `app/src/main/kotlin/com/khomenote/di/AppContainer.kt`

**Interfaces:**
- Consumes: 없음
- Produces: `PhotoStorage.copyToInternalStorage(sourceUri: Uri): String` (suspend, IO 디스패처에서 실행). `AppContainer.photoStorage`. Task 8, 9의 `ReplacementRecordDialog` 사용처에서 소비.

- [ ] **Step 1: PhotoStorage 작성**

`app/src/main/kotlin/com/khomenote/photo/PhotoStorage.kt`:

```kotlin
package com.khomenote.photo

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PhotoStorage(private val context: Context) {
    suspend fun copyToInternalStorage(sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
        val destFile = File(photosDir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(sourceUri).use { input ->
            requireNotNull(input) { "Cannot open input stream for $sourceUri" }
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    }
}
```

- [ ] **Step 2: AppContainer에 photoStorage 추가**

`app/src/main/kotlin/com/khomenote/di/AppContainer.kt` 수정 — import 1개, 필드 1개 추가:

```kotlin
import com.khomenote.photo.PhotoStorage
```

```kotlin
    val photoStorage: PhotoStorage = PhotoStorage(context)
```

(`AssetRepository`/`ConsumableItemRepository`/`ReplacementRecordRepository` 필드들 옆에 나란히 추가. `context`는 이미 `AppContainer(context: Context)` 생성자 파라미터로 있고 `context.applicationContext`로 넘겨받으므로 그대로 재사용.)

- [ ] **Step 3: 빌드 확인**

Run: `cd /c/github/k-home-note && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 버전 올리고 커밋**

`app/build.gradle.kts`의 `versionName`을 `"0.1.3"` → `"0.1.4"`로 수정.

```bash
cd /c/github/k-home-note
git add app/build.gradle.kts app/src/main/kotlin/com/khomenote/photo app/src/main/kotlin/com/khomenote/di/AppContainer.kt
git commit -m "$(cat <<'EOF'
feat: 영수증/제품 사진을 앱 내부 저장소로 복사하는 헬퍼 추가 (v0.1.4)

외부 URI 권한이 만료되는 문제를 피하려고 선택된 이미지를
filesDir/photos/로 복사해두고 그 절대경로를 DB에 저장한다.
EOF
)"
```

---

## Task 6: 알림 (NotificationHelper / ReminderWorker / 스케줄링)

**Files:**
- Create: `app/src/main/kotlin/com/khomenote/notification/NotificationHelper.kt`
- Create: `app/src/main/kotlin/com/khomenote/notification/ReminderWorker.kt`
- Create: `app/src/main/kotlin/com/khomenote/notification/ReminderScheduler.kt`
- Modify: `app/src/main/kotlin/com/khomenote/KHomeNoteApp.kt`

**Interfaces:**
- Consumes: `DueDateCalculator`(Task 2), `ReminderCandidate`/`ReminderTargetSelector`(Task 3), `AppContainer.consumableItemRepository`(Task 4)
- Produces: `createReminderChannel(context)`, `showReminderNotification(context, consumableItemId, title, contentText)`, `consumableItemIdFromIntent(intent): Long?`, `scheduleDailyReminderCheck(context)`. `consumableItemIdFromIntent`는 Task 10의 알림 딥링크에서 사용.

- [ ] **Step 1: 알림 채널/발사/딥링크 파싱 헬퍼 작성**

`app/src/main/kotlin/com/khomenote/notification/NotificationHelper.kt`:

```kotlin
package com.khomenote.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.khomenote.MainActivity
import com.khomenote.R

const val REMINDER_CHANNEL_ID = "reminder"
private const val EXTRA_CONSUMABLE_ITEM_ID = "consumableItemId"

fun createReminderChannel(context: Context) {
    val channel = NotificationChannel(
        REMINDER_CHANNEL_ID,
        "교체 알림",
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply { description = "소모품 교체 예정일 알림" }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(channel)
}

fun showReminderNotification(context: Context, consumableItemId: Long, title: String, contentText: String) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(EXTRA_CONSUMABLE_ITEM_ID, consumableItemId)
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        consumableItemId.toInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(contentText)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.notify(consumableItemId.toInt(), notification)
}

fun consumableItemIdFromIntent(intent: Intent?): Long? =
    intent?.getLongExtra(EXTRA_CONSUMABLE_ITEM_ID, -1L)?.takeIf { it != -1L }
```

- [ ] **Step 2: 하루 1회 스캔하는 CoroutineWorker 작성**

`app/src/main/kotlin/com/khomenote/notification/ReminderWorker.kt`:

```kotlin
package com.khomenote.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.khomenote.di.AppContainer
import com.khomenote.domain.DueDateCalculator
import com.khomenote.domain.ReminderCandidate
import com.khomenote.domain.ReminderTargetSelector
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.getInstance(applicationContext)
        val itemsWithStatus = container.consumableItemRepository.getAllWithStatus().first()
        val today = LocalDate.now()

        val candidates = itemsWithStatus.map { status ->
            ReminderCandidate(
                consumableItemId = status.item.id,
                nextDueDate = DueDateCalculator.nextDueDate(
                    itemCreatedAt = status.item.createdAt,
                    cycleDays = status.item.cycleDays,
                    latestRecordDate = status.latestRecordDate,
                ),
                reminderLeadDays = status.item.reminderLeadDays,
            )
        }

        val targets = ReminderTargetSelector.selectTargets(candidates, today)
        targets.forEach { target ->
            val status = itemsWithStatus.first { it.item.id == target.consumableItemId }
            val daysLeft = ChronoUnit.DAYS.between(today, target.nextDueDate)
            val contentText = if (daysLeft <= 0) "오늘이 교체일이에요" else "${daysLeft}일 후 교체 예정이에요"
            showReminderNotification(
                context = applicationContext,
                consumableItemId = target.consumableItemId,
                title = "${status.assetName} · ${status.item.name}",
                contentText = contentText,
            )
        }
        return Result.success()
    }
}
```

- [ ] **Step 3: WorkManager 스케줄링 함수 작성**

`app/src/main/kotlin/com/khomenote/notification/ReminderScheduler.kt`:

```kotlin
package com.khomenote.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val REMINDER_WORK_NAME = "reminder_daily_check"

fun scheduleDailyReminderCheck(context: Context) {
    val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        REMINDER_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}
```

`ExistingPeriodicWorkPolicy.KEEP`을 쓰는 이유: 앱이 재시작될 때마다 `Application.onCreate()`가 다시 스케줄을 걸려고 시도하는데, 이미 걸려있는 주기 작업을 매번 취소하고 새로 걸면 그때마다 다음 실행 시각이 24시간 뒤로 밀린다. `KEEP`은 이미 예약된 작업이 있으면 그대로 두고 새 요청을 무시한다.

- [ ] **Step 4: Application에서 채널 생성 + 스케줄링**

`app/src/main/kotlin/com/khomenote/KHomeNoteApp.kt` 전체를 다음으로 교체:

```kotlin
package com.khomenote

import android.app.Application
import com.khomenote.notification.createReminderChannel
import com.khomenote.notification.scheduleDailyReminderCheck

class KHomeNoteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createReminderChannel(this)
        scheduleDailyReminderCheck(this)
    }
}
```

- [ ] **Step 5: 빌드 확인**

Run: `cd /c/github/k-home-note && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 실기기에서 WorkManager 등록 확인 (adb, 값 확인이라 직접 해도 됨)**

```bash
adb install -r /c/github/k-home-note/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.khomenote/.MainActivity
adb shell dumpsys jobscheduler | grep -A5 com.khomenote
```
Expected: `com.khomenote`로 등록된 job이 하나 보인다 (WorkManager가 내부적으로 JobScheduler를 씀). 이건 화면을 보는 게 아니라 시스템 서비스 상태를 읽는 것이므로 adb로 직접 확인해도 되는 항목이다.

- [ ] **Step 7: 버전 올리고 커밋**

`app/build.gradle.kts`의 `versionName`을 `"0.1.4"` → `"0.1.5"`로 수정.

```bash
cd /c/github/k-home-note
git add app/build.gradle.kts app/src/main/kotlin/com/khomenote/notification app/src/main/kotlin/com/khomenote/KHomeNoteApp.kt
git commit -m "$(cat <<'EOF'
feat: 교체 예정일 알림(WorkManager 하루 1회 스캔) 추가 (v0.1.5)

정확한 시각 보장이 필요 없는 성격이라 AlarmManager 대신
WorkManager PeriodicWorkRequest로 단순하게 구현.
EOF
)"
```

---

## Task 7: 자산 관리 화면 (목록/추가·수정/상세)

**Files:**
- Create: `app/src/main/kotlin/com/khomenote/ui/asset/AssetListScreen.kt`
- Create: `app/src/main/kotlin/com/khomenote/ui/asset/AssetFormScreen.kt`
- Create: `app/src/main/kotlin/com/khomenote/ui/asset/AssetDetailScreen.kt`

**Interfaces:**
- Consumes: `AssetRepository`, `ConsumableItemRepository`(Task 4)
- Produces: `AssetListScreen(viewModel, onAddAsset, onAssetClick, onBack)`, `AssetFormScreen(viewModel, isNew, onSaved, onBack)`, `AssetDetailScreen(viewModel, onEditAsset, onAddItem, onItemClick, onBack)`와 각각의 ViewModel+Factory. Task 10의 `MainActivity`에서 조립.

- [ ] **Step 1: 자산 목록 화면 작성**

`app/src/main/kotlin/com/khomenote/ui/asset/AssetListScreen.kt`:

```kotlin
package com.khomenote.ui.asset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.entity.AssetCategory
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.repository.AssetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssetListViewModel(private val assetRepository: AssetRepository) : ViewModel() {
    val assets: StateFlow<List<AssetEntity>> = assetRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(asset: AssetEntity) {
        viewModelScope.launch { assetRepository.delete(asset) }
    }
}

class AssetListViewModelFactory(
    private val assetRepository: AssetRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetListViewModel(assetRepository) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetListScreen(
    viewModel: AssetListViewModel,
    onAddAsset: () -> Unit,
    onAssetClick: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<AssetEntity?>(null) }

    pendingDelete?.let { asset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("자산을 삭제할까요?") },
            text = { Text("\"${asset.name}\"과 딸린 소모품·이력이 모두 삭제되며 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(asset)
                    pendingDelete = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("자산 관리") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddAsset) { Text("자산 추가") }
        },
    ) { padding ->
        if (assets.isEmpty()) {
            Text(
                "등록된 자산이 없습니다. \"자산 추가\" 버튼을 눌러 추가하세요.",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(assets, key = { it.id }) { asset ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onAssetClick(asset.id) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(asset.name, fontWeight = FontWeight.Bold)
                        Text(if (asset.category == AssetCategory.HOME) "가전" else "자동차")
                    }
                    TextButton(onClick = { pendingDelete = asset }) { Text("삭제") }
                }
                HorizontalDivider()
            }
        }
    }
}
```

- [ ] **Step 2: 자산 추가/수정 화면 작성**

`app/src/main/kotlin/com/khomenote/ui/asset/AssetFormScreen.kt`:

```kotlin
package com.khomenote.ui.asset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.entity.AssetCategory
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.repository.AssetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class AssetFormViewModel(
    private val assetRepository: AssetRepository,
    private val assetId: Long?,
) : ViewModel() {
    var name by mutableStateOf("")
        private set
    var category by mutableStateOf(AssetCategory.HOME)
        private set
    var memo by mutableStateOf("")
        private set

    // 수정 시 원래 등록일을 보존한다 — 이 값을 안 챙기면 저장할 때마다
    // AssetEntity 기본값(LocalDate.now())으로 등록일이 오늘로 초기화돼버린다.
    private var loadedCreatedAt: LocalDate = LocalDate.now()

    init {
        if (assetId != null) {
            viewModelScope.launch {
                val asset = assetRepository.getById(assetId).first() ?: return@launch
                name = asset.name
                category = asset.category
                memo = asset.memo ?: ""
                loadedCreatedAt = asset.createdAt
            }
        }
    }

    fun onNameChange(value: String) { name = value }
    fun onCategoryChange(value: AssetCategory) { category = value }
    fun onMemoChange(value: String) { memo = value }

    fun canSave(): Boolean = name.isNotBlank()

    fun save(onDone: () -> Unit) {
        if (!canSave()) return
        viewModelScope.launch {
            assetRepository.save(
                AssetEntity(
                    id = assetId ?: 0,
                    name = name,
                    category = category,
                    memo = memo.takeIf { it.isNotBlank() },
                    createdAt = loadedCreatedAt,
                )
            )
            onDone()
        }
    }
}

class AssetFormViewModelFactory(
    private val assetRepository: AssetRepository,
    private val assetId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetFormViewModel(assetRepository, assetId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetFormScreen(
    viewModel: AssetFormViewModel,
    isNew: Boolean,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "자산 추가" else "자산 수정") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onSaved) },
                        enabled = viewModel.canSave(),
                    ) { Text("저장") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("이름 (예: 다이슨 V15, 아반떼 하이브리드)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                FilterChip(
                    selected = viewModel.category == AssetCategory.HOME,
                    onClick = { viewModel.onCategoryChange(AssetCategory.HOME) },
                    label = { Text("가전") },
                )
                FilterChip(
                    selected = viewModel.category == AssetCategory.CAR,
                    onClick = { viewModel.onCategoryChange(AssetCategory.CAR) },
                    label = { Text("자동차") },
                )
            }
            OutlinedTextField(
                value = viewModel.memo,
                onValueChange = viewModel::onMemoChange,
                label = { Text("메모") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

- [ ] **Step 3: 자산 상세(소속 소모품 목록) 화면 작성**

`app/src/main/kotlin/com/khomenote/ui/asset/AssetDetailScreen.kt`:

```kotlin
package com.khomenote.ui.asset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.entity.AssetCategory
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.ConsumableItemRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssetDetailUiState(
    val asset: AssetEntity? = null,
    val items: List<ConsumableItemEntity> = emptyList(),
)

class AssetDetailViewModel(
    assetRepository: AssetRepository,
    private val consumableItemRepository: ConsumableItemRepository,
    assetId: Long,
) : ViewModel() {
    val uiState: StateFlow<AssetDetailUiState> = combine(
        assetRepository.getById(assetId),
        consumableItemRepository.getByAsset(assetId),
    ) { asset, items -> AssetDetailUiState(asset, items) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AssetDetailUiState())

    fun deleteItem(item: ConsumableItemEntity) {
        viewModelScope.launch { consumableItemRepository.delete(item) }
    }
}

class AssetDetailViewModelFactory(
    private val assetRepository: AssetRepository,
    private val consumableItemRepository: ConsumableItemRepository,
    private val assetId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AssetDetailViewModel(assetRepository, consumableItemRepository, assetId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    viewModel: AssetDetailViewModel,
    onEditAsset: () -> Unit,
    onAddItem: () -> Unit,
    onItemClick: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ConsumableItemEntity?>(null) }
    val asset = uiState.asset

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("소모품을 삭제할까요?") },
            text = { Text("\"${item.name}\"과 교체 이력이 모두 삭제되며 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteItem(item)
                    pendingDelete = null
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("취소") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(asset?.name ?: "자산") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
                actions = { TextButton(onClick = onEditAsset) { Text("수정") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddItem) { Text("소모품 추가") }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (asset != null) {
                Text(
                    if (asset.category == AssetCategory.HOME) "가전" else "자동차",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (uiState.items.isEmpty()) {
                Text(
                    "등록된 소모품이 없습니다. \"소모품 추가\" 버튼을 눌러 추가하세요.",
                    modifier = Modifier.padding(16.dp),
                )
                return@Scaffold
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.items, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onItemClick(item.id) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { pendingDelete = item }) { Text("삭제") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
```

- [ ] **Step 4: 빌드 확인**

Run: `cd /c/github/k-home-note && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL` (아직 `MainActivity`에서 이 화면들을 쓰지 않으므로 미사용 경고만 있을 수 있음 — 에러는 없어야 한다)

- [ ] **Step 5: 버전 올리고 커밋**

`app/build.gradle.kts`의 `versionName`을 `"0.1.5"` → `"0.1.6"`로 수정.

```bash
cd /c/github/k-home-note
git add app/build.gradle.kts app/src/main/kotlin/com/khomenote/ui/asset
git commit -m "$(cat <<'EOF'
feat: 자산 관리 화면 추가 (v0.1.6)

목록/추가·수정/상세(소속 소모품 목록) 3개 화면. golf-round-tracker의
CourseManage/CourseEdit 화면 패턴(같은 파일에 ViewModel+Factory co-locate)을 따름.
EOF
)"
```

---

## Task 8: 소모품 항목 상세/추가·수정 화면 + 교체완료 기록 다이얼로그

**Files:**
- Create: `app/src/main/kotlin/com/khomenote/ui/item/ReplacementRecordDialog.kt`
- Create: `app/src/main/kotlin/com/khomenote/ui/item/ItemDetailScreen.kt`
- Create: `app/src/main/kotlin/com/khomenote/ui/item/ItemFormScreen.kt`

**Interfaces:**
- Consumes: `ConsumableItemRepository`, `ReplacementRecordRepository`(Task 4), `PhotoStorage`(Task 5), `DueDateCalculator`(Task 2)
- Produces: `ReplacementRecordDialog(onDismiss, onSave, onPickPhoto)`+`ReplacementRecordInput` (Task 9 HomeScreen이 그대로 재사용), `ItemDetailScreen(viewModel, onEditItem, onDeleted, onBack)`, `ItemFormScreen(viewModel, isNew, onSaved, onBack)`와 각 ViewModel+Factory. Task 10에서 조립.

- [ ] **Step 1: 공용 "교체 완료 기록" 다이얼로그 작성**

홈 대시보드(Task 9)와 소모품 상세(이 태스크)가 똑같은 입력 폼을 쓰므로 한 곳에서 만들어 재사용한다(DRY).

`app/src/main/kotlin/com/khomenote/ui/item/ReplacementRecordDialog.kt`:

```kotlin
package com.khomenote.ui.item

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ReplacementRecordInput(
    val date: LocalDate,
    val price: Long?,
    val vendor: String?,
    val photoUri: String?,
    val memo: String?,
)

@Composable
fun ReplacementRecordDialog(
    onDismiss: () -> Unit,
    onSave: (ReplacementRecordInput) -> Unit,
    onPickPhoto: suspend (Uri) -> String,
) {
    var priceText by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { photoPath = onPickPhoto(uri) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("교체 완료 기록") },
        text = {
            Column {
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    label = { Text("가격 (원)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("구매처/링크") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                TextButton(onClick = { photoPicker.launch("image/*") }) {
                    Text(if (photoPath == null) "영수증/제품 사진 첨부" else "사진 첨부됨 (다시 선택하려면 클릭)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    ReplacementRecordInput(
                        date = LocalDate.now(),
                        price = priceText.toLongOrNull(),
                        vendor = vendor.takeIf { it.isNotBlank() },
                        photoUri = photoPath,
                        memo = memo.takeIf { it.isNotBlank() },
                    )
                )
            }) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
```

- [ ] **Step 2: 소모품 상세 화면 작성**

`app/src/main/kotlin/com/khomenote/ui/item/ItemDetailScreen.kt`:

```kotlin
package com.khomenote.ui.item

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.ReplacementRecordEntity
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.ReplacementRecordRepository
import com.khomenote.domain.DueDateCalculator
import com.khomenote.photo.PhotoStorage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

data class ItemDetailUiState(
    val status: ConsumableItemWithStatus? = null,
    val records: List<ReplacementRecordEntity> = emptyList(),
)

class ItemDetailViewModel(
    private val consumableItemRepository: ConsumableItemRepository,
    private val replacementRecordRepository: ReplacementRecordRepository,
    private val photoStorage: PhotoStorage,
    private val itemId: Long,
) : ViewModel() {

    val uiState: StateFlow<ItemDetailUiState> = combine(
        consumableItemRepository.getWithStatusById(itemId),
        replacementRecordRepository.getByItem(itemId),
    ) { status, records -> ItemDetailUiState(status, records) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ItemDetailUiState())

    fun recordReplacement(date: LocalDate, price: Long?, vendor: String?, photoUri: String?, memo: String?) {
        viewModelScope.launch {
            replacementRecordRepository.add(
                ReplacementRecordEntity(
                    consumableItemId = itemId,
                    date = date,
                    price = price,
                    vendor = vendor,
                    photoUri = photoUri,
                    memo = memo,
                )
            )
        }
    }

    suspend fun pickPhoto(uri: Uri): String = photoStorage.copyToInternalStorage(uri)

    fun deleteItem(onDone: () -> Unit) {
        val item = uiState.value.status?.item ?: return
        viewModelScope.launch {
            consumableItemRepository.delete(item)
            onDone()
        }
    }
}

class ItemDetailViewModelFactory(
    private val consumableItemRepository: ConsumableItemRepository,
    private val replacementRecordRepository: ReplacementRecordRepository,
    private val photoStorage: PhotoStorage,
    private val itemId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ItemDetailViewModel(consumableItemRepository, replacementRecordRepository, photoStorage, itemId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    viewModel: ItemDetailViewModel,
    onEditItem: () -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showRecordDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val status = uiState.status

    if (showRecordDialog) {
        ReplacementRecordDialog(
            onDismiss = { showRecordDialog = false },
            onSave = { input ->
                viewModel.recordReplacement(input.date, input.price, input.vendor, input.photoUri, input.memo)
                showRecordDialog = false
            },
            onPickPhoto = { uri -> viewModel.pickPhoto(uri) },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("소모품을 삭제할까요?") },
            text = { Text("교체 이력이 모두 삭제되며 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteItem(onDeleted)
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(status?.item?.name ?: "소모품") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
                actions = {
                    TextButton(onClick = onEditItem) { Text("수정") }
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                },
            )
        },
    ) { padding ->
        if (status == null) {
            Text("불러오는 중...", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        val nextDueDate = DueDateCalculator.nextDueDate(
            itemCreatedAt = status.item.createdAt,
            cycleDays = status.item.cycleDays,
            latestRecordDate = status.latestRecordDate,
        )
        val daysUntilDue = DueDateCalculator.daysUntilDue(nextDueDate, LocalDate.now())

        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("${status.assetName} · ${status.item.name}", fontWeight = FontWeight.Bold)
            Text("주기: ${status.item.cycleDays}일 / 알림: ${status.item.reminderLeadDays}일 전")
            Text(
                "다음 예정일: $nextDueDate" + if (daysUntilDue <= 0) " (이미 지남)" else " (D-$daysUntilDue)",
                color = if (daysUntilDue <= 0) Color.Red else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            if (!status.item.memo.isNullOrBlank()) {
                Text(status.item.memo, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { showRecordDialog = true }) { Text("교체 완료") }
            Spacer(Modifier.height(16.dp))
            Text("교체/구매 이력", fontWeight = FontWeight.Bold)
            if (uiState.records.isEmpty()) {
                Text("아직 기록된 이력이 없습니다.", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(uiState.records, key = { it.id }) { record ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        if (record.photoUri != null) {
                            AsyncImage(
                                model = File(record.photoUri),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Column {
                            Text(record.date.toString(), fontWeight = FontWeight.Bold)
                            val priceText = record.price?.let { "%,d원".format(it) }
                            val infoLine = listOfNotNull(priceText, record.vendor).joinToString(" · ")
                            if (infoLine.isNotBlank()) Text(infoLine, style = MaterialTheme.typography.bodySmall)
                            if (!record.memo.isNullOrBlank()) Text(record.memo, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
```

- [ ] **Step 3: 소모품 추가/수정 화면 작성**

`app/src/main/kotlin/com/khomenote/ui/item/ItemFormScreen.kt`:

```kotlin
package com.khomenote.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.repository.ConsumableItemRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class CycleUnit(val days: Int, val label: String) {
    DAYS(1, "일"),
    MONTHS(30, "개월"),
    YEARS(365, "년"),
}

class ItemFormViewModel(
    private val consumableItemRepository: ConsumableItemRepository,
    assetIdForNew: Long?,
    private val itemId: Long?,
) : ViewModel() {
    var name by mutableStateOf("")
        private set
    var cycleUnit by mutableStateOf(CycleUnit.MONTHS)
        private set
    var cycleValueText by mutableStateOf("6")
        private set
    var reminderLeadDaysText by mutableStateOf("7")
        private set
    var memo by mutableStateOf("")
        private set

    private var loadedAssetId: Long? = assetIdForNew

    // 수정 시 원래 등록일을 보존한다 — AssetFormViewModel과 같은 이유로,
    // 안 챙기면 저장할 때마다 등록일이 오늘로 리셋되어 다음 예정일 계산이 틀어진다.
    private var loadedCreatedAt: LocalDate = LocalDate.now()

    val cycleDays: Int
        get() = (cycleValueText.toIntOrNull() ?: 0) * cycleUnit.days

    init {
        if (itemId != null) {
            viewModelScope.launch {
                val item = consumableItemRepository.getById(itemId).first() ?: return@launch
                name = item.name
                cycleUnit = CycleUnit.DAYS
                cycleValueText = item.cycleDays.toString()
                reminderLeadDaysText = item.reminderLeadDays.toString()
                memo = item.memo ?: ""
                loadedAssetId = item.assetId
                loadedCreatedAt = item.createdAt
            }
        }
    }

    fun onNameChange(value: String) { name = value }
    fun onCycleUnitChange(value: CycleUnit) { cycleUnit = value }
    fun onCycleValueChange(value: String) { cycleValueText = value.filter(Char::isDigit) }
    fun onReminderLeadDaysChange(value: String) { reminderLeadDaysText = value.filter(Char::isDigit) }
    fun onMemoChange(value: String) { memo = value }

    fun canSave(): Boolean = name.isNotBlank() && cycleDays > 0 && loadedAssetId != null

    fun save(onDone: () -> Unit) {
        val assetId = loadedAssetId ?: return
        if (!canSave()) return
        viewModelScope.launch {
            consumableItemRepository.save(
                ConsumableItemEntity(
                    id = itemId ?: 0,
                    assetId = assetId,
                    name = name,
                    cycleDays = cycleDays,
                    reminderLeadDays = reminderLeadDaysText.toIntOrNull() ?: 7,
                    memo = memo.takeIf { it.isNotBlank() },
                    createdAt = loadedCreatedAt,
                )
            )
            onDone()
        }
    }
}

class ItemFormViewModelFactory(
    private val consumableItemRepository: ConsumableItemRepository,
    private val assetIdForNew: Long?,
    private val itemId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ItemFormViewModel(consumableItemRepository, assetIdForNew, itemId) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemFormScreen(
    viewModel: ItemFormViewModel,
    isNew: Boolean,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "소모품 추가" else "소모품 수정") },
                navigationIcon = { TextButton(onClick = onBack) { Text("< 뒤로") } },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onSaved) },
                        enabled = viewModel.canSave(),
                    ) { Text("저장") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("이름 (예: 필터, 엔진오일)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            Text("교체 주기")
            OutlinedTextField(
                value = viewModel.cycleValueText,
                onValueChange = viewModel::onCycleValueChange,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                CycleUnit.entries.forEach { unit ->
                    FilterChip(
                        selected = viewModel.cycleUnit == unit,
                        onClick = { viewModel.onCycleUnitChange(unit) },
                        label = { Text(unit.label) },
                    )
                }
            }
            OutlinedTextField(
                value = viewModel.reminderLeadDaysText,
                onValueChange = viewModel::onReminderLeadDaysChange,
                label = { Text("며칠 전에 알림을 받을까요?") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = viewModel.memo,
                onValueChange = viewModel::onMemoChange,
                label = { Text("메모") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```

- [ ] **Step 4: 빌드 확인**

Run: `cd /c/github/k-home-note && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 버전 올리고 커밋**

`app/build.gradle.kts`의 `versionName`을 `"0.1.6"` → `"0.1.7"`로 수정.

```bash
cd /c/github/k-home-note
git add app/build.gradle.kts app/src/main/kotlin/com/khomenote/ui/item
git commit -m "$(cat <<'EOF'
feat: 소모품 상세/추가·수정 화면과 교체완료 기록 다이얼로그 추가 (v0.1.7)

ReplacementRecordDialog는 홈 대시보드(다음 태스크)와 공유하는
공용 컴포넌트로 분리했다.
EOF
)"
```

---

## Task 9: 홈 대시보드 화면

**Files:**
- Create: `app/src/main/kotlin/com/khomenote/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `ConsumableItemRepository`, `ReplacementRecordRepository`(Task 4), `PhotoStorage`(Task 5), `DueDateCalculator`(Task 2), `ReplacementRecordDialog`/`ReplacementRecordInput`(Task 8)
- Produces: `HomeScreen(viewModel, onManageAssets, onItemClick)` + `HomeViewModel`/`HomeViewModelFactory`. Task 10에서 조립.

- [ ] **Step 1: 홈 대시보드 화면 작성**

`app/src/main/kotlin/com/khomenote/ui/home/HomeScreen.kt`:

```kotlin
package com.khomenote.ui.home

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.AssetCategory
import com.khomenote.data.local.entity.ReplacementRecordEntity
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.ReplacementRecordRepository
import com.khomenote.domain.DueDateCalculator
import com.khomenote.photo.PhotoStorage
import com.khomenote.ui.item.ReplacementRecordDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeItemUiModel(
    val status: ConsumableItemWithStatus,
    val nextDueDate: LocalDate,
    val daysUntilDue: Long,
)

class HomeViewModel(
    private val consumableItemRepository: ConsumableItemRepository,
    private val replacementRecordRepository: ReplacementRecordRepository,
    private val photoStorage: PhotoStorage,
) : ViewModel() {

    // 정렬 기준이 nextDueDate 오름차순이라, 연체(과거 날짜) 항목이 자연히
    // 맨 앞에 온다 — 화면에서 별도로 "연체는 위로" 정렬 로직을 두지 않아도 된다.
    val items: StateFlow<List<HomeItemUiModel>> = consumableItemRepository.getAllWithStatus()
        .map { list ->
            list.map { status ->
                val nextDueDate = DueDateCalculator.nextDueDate(
                    itemCreatedAt = status.item.createdAt,
                    cycleDays = status.item.cycleDays,
                    latestRecordDate = status.latestRecordDate,
                )
                HomeItemUiModel(
                    status = status,
                    nextDueDate = nextDueDate,
                    daysUntilDue = DueDateCalculator.daysUntilDue(nextDueDate, LocalDate.now()),
                )
            }.sortedBy { it.nextDueDate }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun recordReplacement(itemId: Long, date: LocalDate, price: Long?, vendor: String?, photoUri: String?, memo: String?) {
        viewModelScope.launch {
            replacementRecordRepository.add(
                ReplacementRecordEntity(
                    consumableItemId = itemId,
                    date = date,
                    price = price,
                    vendor = vendor,
                    photoUri = photoUri,
                    memo = memo,
                )
            )
        }
    }

    suspend fun pickPhoto(uri: Uri): String = photoStorage.copyToInternalStorage(uri)
}

class HomeViewModelFactory(
    private val consumableItemRepository: ConsumableItemRepository,
    private val replacementRecordRepository: ReplacementRecordRepository,
    private val photoStorage: PhotoStorage,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(consumableItemRepository, replacementRecordRepository, photoStorage) as T
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onManageAssets: () -> Unit,
    onItemClick: (Long) -> Unit,
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var categoryFilter by remember { mutableStateOf<AssetCategory?>(null) }
    var recordDialogItemId by remember { mutableStateOf<Long?>(null) }

    val filteredItems = remember(items, categoryFilter) {
        items.filter { categoryFilter == null || it.status.assetCategory == categoryFilter }
    }

    recordDialogItemId?.let { itemId ->
        ReplacementRecordDialog(
            onDismiss = { recordDialogItemId = null },
            onSave = { input ->
                viewModel.recordReplacement(itemId, input.date, input.price, input.vendor, input.photoUri, input.memo)
                recordDialogItemId = null
            },
            onPickPhoto = { uri -> viewModel.pickPhoto(uri) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("정비 노트") },
                actions = { TextButton(onClick = onManageAssets) { Text("자산 관리") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("전체") })
                FilterChip(
                    selected = categoryFilter == AssetCategory.HOME,
                    onClick = { categoryFilter = AssetCategory.HOME },
                    label = { Text("가전") },
                )
                FilterChip(
                    selected = categoryFilter == AssetCategory.CAR,
                    onClick = { categoryFilter = AssetCategory.CAR },
                    label = { Text("자동차") },
                )
            }
            if (filteredItems.isEmpty()) {
                Text(
                    "등록된 소모품이 없습니다. \"자산 관리\"에서 자산과 소모품을 먼저 추가하세요.",
                    modifier = Modifier.padding(16.dp),
                )
                return@Scaffold
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredItems, key = { it.status.item.id }) { uiModel ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onItemClick(uiModel.status.item.id) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${uiModel.status.assetName} · ${uiModel.status.item.name}",
                                fontWeight = FontWeight.Bold,
                            )
                            val dDayText = when {
                                uiModel.daysUntilDue < 0 -> "${-uiModel.daysUntilDue}일 지남"
                                uiModel.daysUntilDue == 0L -> "오늘"
                                else -> "D-${uiModel.daysUntilDue}"
                            }
                            Text(
                                dDayText,
                                color = if (uiModel.daysUntilDue <= 0) Color.Red else MaterialTheme.colorScheme.outline,
                                fontWeight = if (uiModel.daysUntilDue <= 0) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                        TextButton(onClick = { recordDialogItemId = uiModel.status.item.id }) { Text("교체 완료") }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd /c/github/k-home-note && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 버전 올리고 커밋**

`app/build.gradle.kts`의 `versionName`을 `"0.1.7"` → `"0.1.8"`로 수정.

```bash
cd /c/github/k-home-note
git add app/build.gradle.kts app/src/main/kotlin/com/khomenote/ui/home
git commit -m "$(cat <<'EOF'
feat: 홈 대시보드 화면 추가 (v0.1.8)

D-day 오름차순 정렬(연체 항목 자연히 상단 고정), 카테고리 필터,
리스트에서 바로 교체완료를 기록하는 퀵액션 포함.
EOF
)"
```

---

## Task 10: 내비게이션 조립 + 알림 딥링크 + 알림 권한 요청

**Files:**
- Create: `app/src/main/kotlin/com/khomenote/ui/navigation/Screen.kt`
- Modify: `app/src/main/kotlin/com/khomenote/MainActivity.kt` (Task 1의 스텁 전체 교체)

**Interfaces:**
- Consumes: 모든 이전 태스크의 화면/ViewModel/Factory, `consumableItemIdFromIntent`(Task 6), `AppContainer`(Task 4·5)
- Produces: 완성된 앱 진입점. 이 태스크 이후로 추가 소비자 없음(최종 조립 지점).

- [ ] **Step 1: 화면 백스택 타입 작성**

`app/src/main/kotlin/com/khomenote/ui/navigation/Screen.kt`:

```kotlin
package com.khomenote.ui.navigation

/** golf-round-tracker와 동일하게 별도 Navigation 라이브러리 없이 수동 백스택으로 처리한다. */
sealed interface Screen {
    data object Home : Screen
    data object AssetList : Screen
    data class AssetForm(val assetId: Long?) : Screen
    data class AssetDetail(val assetId: Long) : Screen
    /** [assetId]는 새 항목을 만들 때만 필요하다. 수정([itemId]가 있는 경우)은
     * ItemFormViewModel이 기존 항목에서 assetId를 읽어오므로 null로 넘겨도 된다. */
    data class ItemForm(val assetId: Long?, val itemId: Long?) : Screen
    data class ItemDetail(val itemId: Long) : Screen
}
```

- [ ] **Step 2: MainActivity를 전체 조립 코드로 교체**

`app/src/main/kotlin/com/khomenote/MainActivity.kt` 전체를 다음으로 교체:

```kotlin
package com.khomenote

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.khomenote.di.AppContainer
import com.khomenote.notification.consumableItemIdFromIntent
import com.khomenote.ui.asset.AssetDetailScreen
import com.khomenote.ui.asset.AssetDetailViewModel
import com.khomenote.ui.asset.AssetDetailViewModelFactory
import com.khomenote.ui.asset.AssetFormScreen
import com.khomenote.ui.asset.AssetFormViewModel
import com.khomenote.ui.asset.AssetFormViewModelFactory
import com.khomenote.ui.asset.AssetListScreen
import com.khomenote.ui.asset.AssetListViewModel
import com.khomenote.ui.asset.AssetListViewModelFactory
import com.khomenote.ui.home.HomeScreen
import com.khomenote.ui.home.HomeViewModel
import com.khomenote.ui.home.HomeViewModelFactory
import com.khomenote.ui.item.ItemDetailScreen
import com.khomenote.ui.item.ItemDetailViewModel
import com.khomenote.ui.item.ItemDetailViewModelFactory
import com.khomenote.ui.item.ItemFormScreen
import com.khomenote.ui.item.ItemFormViewModel
import com.khomenote.ui.item.ItemFormViewModelFactory
import com.khomenote.ui.navigation.Screen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = AppContainer.getInstance(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { /* 거부해도 앱은 그대로 쓸 수 있다 — 이후 알림만 조용히 안 뜬다(NotificationHelper가 권한 체크 후 스킵) */ }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        consumableItemIdFromIntent(intent)?.let { itemId ->
                            backStack.add(Screen.ItemDetail(itemId))
                        }
                    }

                    AppRoot(container = container, backStack = backStack)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
private fun AppRoot(container: AppContainer, backStack: SnapshotStateList<Screen>) {
    val current = backStack.last()

    fun push(screen: Screen) { backStack.add(screen) }
    fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    BackHandler(enabled = backStack.size > 1) { pop() }

    when (val screen = current) {
        is Screen.Home -> {
            val vm = viewModel<HomeViewModel>(
                factory = HomeViewModelFactory(
                    container.consumableItemRepository,
                    container.replacementRecordRepository,
                    container.photoStorage,
                ),
            )
            HomeScreen(
                viewModel = vm,
                onManageAssets = { push(Screen.AssetList) },
                onItemClick = { itemId -> push(Screen.ItemDetail(itemId)) },
            )
        }

        is Screen.AssetList -> {
            val vm = viewModel<AssetListViewModel>(
                factory = AssetListViewModelFactory(container.assetRepository),
            )
            AssetListScreen(
                viewModel = vm,
                onAddAsset = { push(Screen.AssetForm(null)) },
                onAssetClick = { assetId -> push(Screen.AssetDetail(assetId)) },
                onBack = { pop() },
            )
        }

        is Screen.AssetForm -> {
            val vm = viewModel<AssetFormViewModel>(
                factory = AssetFormViewModelFactory(container.assetRepository, screen.assetId),
                key = "asset-form-${System.identityHashCode(screen)}",
            )
            AssetFormScreen(viewModel = vm, isNew = screen.assetId == null, onSaved = { pop() }, onBack = { pop() })
        }

        is Screen.AssetDetail -> {
            val vm = viewModel<AssetDetailViewModel>(
                factory = AssetDetailViewModelFactory(
                    container.assetRepository,
                    container.consumableItemRepository,
                    screen.assetId,
                ),
            )
            AssetDetailScreen(
                viewModel = vm,
                onEditAsset = { push(Screen.AssetForm(screen.assetId)) },
                onAddItem = { push(Screen.ItemForm(screen.assetId, null)) },
                onItemClick = { itemId -> push(Screen.ItemDetail(itemId)) },
                onBack = { pop() },
            )
        }

        is Screen.ItemForm -> {
            val vm = viewModel<ItemFormViewModel>(
                factory = ItemFormViewModelFactory(container.consumableItemRepository, screen.assetId, screen.itemId),
                key = "item-form-${System.identityHashCode(screen)}",
            )
            ItemFormScreen(viewModel = vm, isNew = screen.itemId == null, onSaved = { pop() }, onBack = { pop() })
        }

        is Screen.ItemDetail -> {
            val vm = viewModel<ItemDetailViewModel>(
                factory = ItemDetailViewModelFactory(
                    container.consumableItemRepository,
                    container.replacementRecordRepository,
                    container.photoStorage,
                    screen.itemId,
                ),
            )
            ItemDetailScreen(
                viewModel = vm,
                onEditItem = { push(Screen.ItemForm(assetId = null, itemId = screen.itemId)) },
                onDeleted = { pop() },
                onBack = { pop() },
            )
        }
    }
}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd /c/github/k-home-note && ./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 실기기에서 전체 플로우 확인 — 사용자에게 확인 요청**

```bash
adb install -r /c/github/k-home-note/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.khomenote/.MainActivity
```

사용자에게 요청 (화면 판단은 adb로 넘겨가며 직접 확인하지 않고 아래처럼 사용자에게 안내):

1. "앱을 열면 알림 권한 팝업이 뜨는지, '허용'을 누르면 팝업이 사라지는지 확인해주세요."
2. "'자산 관리' 버튼을 눌러 자산 목록 화면으로 이동하는지, '자산 추가'로 예를 들어 '테스트 청소기'(가전)를 하나 만들고 저장하면 목록에 나타나는지 확인해주세요."
3. "방금 만든 자산을 탭해서 들어간 뒤 '소모품 추가'로 예를 들어 '필터'(1개월 주기)를 만들고 저장하면, 홈 화면으로 돌아왔을 때 'D-30' 근처 숫자로 목록에 나타나는지 확인해주세요."
4. "그 항목의 '교체 완료' 버튼을 눌러 가격 등을 아무거나 입력하고 저장한 뒤, 항목을 탭해서 상세 화면에 방금 입력한 이력이 나타나는지, 사진을 첨부했다면 작은 썸네일이 보이는지 확인해주세요."
5. "뒤로가기 버튼으로 홈까지 잘 돌아와지는지 확인해주세요."

문제가 있으면 어떤 단계에서 무엇이 다르게 보였는지 알려달라고 요청한다.

- [ ] **Step 5: 버전 올리고 커밋**

`app/build.gradle.kts`의 `versionName`을 `"0.1.8"` → `"0.1.9"`로 수정.

```bash
cd /c/github/k-home-note
git add app/build.gradle.kts app/src/main/kotlin/com/khomenote/ui/navigation app/src/main/kotlin/com/khomenote/MainActivity.kt
git commit -m "$(cat <<'EOF'
feat: 화면 내비게이션 조립 + 알림 딥링크 + 알림 권한 요청 (v0.1.9)

수동 백스택(Screen sealed interface)으로 6개 화면을 연결하고,
알림 탭 시 해당 소모품 상세로 바로 이동하도록 인텐트 extra를 배선했다.
이것으로 MVP 기능이 전부 연결된 상태.
EOF
)"
```

---

## Self-Review

**1. 스펙 커버리지**

- Asset/ConsumableItem/ReplacementRecord 3단 구조, 다음 예정일 계산 → Task 2, 4
- 홈 대시보드(D-day 정렬, 연체 강조, 카테고리 필터, 퀵 교체완료) → Task 9
- 자산 관리(목록/추가/수정/삭제, 자산별 소모품 목록) → Task 7
- 소모품 항목 상세(이력, 알림 리드타임, 교체완료)/추가 → Task 8
- 알림(하루 1회 스캔, D-리드타임과 D-day 발사, 탭 시 딥링크) → Task 6, 10
- 사진(선택→내부 저장→상세에 썸네일) → Task 5, 8
- 워크스페이스 규칙(버전 올리기, adb 화면 확인 금지) → 모든 태스크의 마지막 두 스텝
- 빠진 것 없음.

**2. Placeholder 스캔**

전체 태스크에 "TBD"/"적절히"/"필요시 구현" 류 문구 없음. 모든 코드 스텝에 실제 컴파일 가능한 코드가 들어있다.

**3. 타입/시그니처 일관성 확인 및 초안 작성 중 고친 버그**

- `AssetFormViewModel.save()`와 `ItemFormViewModel.save()`에서, 처음 초안에는 `createdAt`을 안 넘겨서 `AssetEntity`/`ConsumableItemEntity`의 기본값(`LocalDate.now()`)이 그대로 쓰이는 버그가 있었다 — **수정을 저장할 때마다 원래 등록일이 오늘 날짜로 리셋**되어, 특히 `ConsumableItemEntity.createdAt`은 교체 이력이 하나도 없을 때 다음 예정일 계산의 기준값으로 쓰이므로 실사용에 영향을 주는 버그였다. 두 ViewModel 모두 `init` 블록에서 `loadedCreatedAt`을 읽어와 `save()`에서 그대로 넘기도록 고쳤다(Task 7 Step 2, Task 8 Step 3에 반영됨).
- `ItemFormViewModel`의 최초 초안은 `viewModelScope.launch { repository.getById(itemId).collect { ... } }`로 폼 필드를 채우려 했는데, 이러면 사용자가 입력 중에 DB가 다른 이유로 재emit되면 입력값이 덮어써진다. `.first()`로 최초 1회만 로드하도록 고쳤다.
- `ConsumableItemWithStatus`(DTO)의 필드명(`item`, `assetName`, `assetCategory`, `latestRecordDate`)이 Task 4(DAO 쿼리 별칭 `assetName`/`assetCategory`/`latestRecordDate`), Task 6(`ReminderWorker`), Task 8(`ItemDetailScreen`), Task 9(`HomeScreen`) 전체에서 동일하게 쓰였는지 확인 — 일치함.
- `ReplacementRecordInput`/`ReplacementRecordDialog`가 Task 8에서 정의되고 Task 9(`HomeScreen`)에서 import해 재사용하는 것을 확인 — 시그니처(`onDismiss`, `onSave: (ReplacementRecordInput) -> Unit`, `onPickPhoto: suspend (Uri) -> String`) 동일.
- `Screen.ItemForm(assetId, itemId)`를 만드는 두 지점(Task 10의 `AssetDetail`에서는 `assetId=screen.assetId, itemId=null`, `ItemDetail`에서는 `assetId=null, itemId=screen.itemId`) 모두 `ItemFormViewModelFactory(consumableItemRepository, assetIdForNew, itemId)` 시그니처와 일치.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-08-15-maintenance-note-plan.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - 태스크마다 새 subagent를 붙여 구현시키고, 태스크 사이마다 리뷰한다. 빠르게 반복 가능.

**2. Inline Execution** - 지금 세션에서 태스크를 순서대로 직접 실행하고, 체크포인트마다 검토한다.

**Which approach?**

**Subagent-Driven을 고르면:** superpowers:subagent-driven-development 사용 — 태스크마다 새 subagent + 2단계 리뷰.
**Inline Execution을 고르면:** superpowers:executing-plans 사용 — 배치 실행 + 체크포인트 검토.

