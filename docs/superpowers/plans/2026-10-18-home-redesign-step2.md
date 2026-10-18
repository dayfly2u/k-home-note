# 생활달력 리디자인 — 2단계(홈 화면: 요약/섹션/행 모양) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 홈 화면을 "청록 헤더 + 요약 3칸 + D-day 구간별 섹션 + 바뀐 행 모양"으로 교체한다. DB 스키마 변경 없음.

**Architecture:** 기존 `HomeViewModel.items: StateFlow<List<HomeRowUiModel>>`는 그대로 두고, 그 리스트를 "구간(DueBand)"으로 나누는 순수 함수 계층(`HomeSummary.kt`)을 새로 추가한다. `HomeScreen.kt`는 이 순수 함수들의 결과만 그려서 보여주는 역할만 한다. D-day 배지 색 로직(`dDayStyle`)도 같은 구간 분류 함수를 공유하도록 리팩터링해서 배지 색과 섹션 분류가 항상 일치하게 한다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), JUnit(순수 함수 단위 테스트). 안드로이드 UI 테스트 프레임워크는 이 프로젝트에 없음 — UI 배치 변경은 빌드+설치+실기기 확인으로 검증한다(아래 각 UI 태스크 참고).

## Global Constraints

- DB 스키마 변경 없음 — `Migration` 작성 불필요(redesign-brief.md 0장 원칙 2).
- 색상·모서리·터치영역 값은 `redesign-brief.md` 1장과 이미 만들어진 `app/src/main/kotlin/com/khomenote/ui/theme/Theme.kt`의 상수(`TealPrimary`, `TealContainer`, `OrangeUrgent`, `OrangeUrgentContainer`, `OrangeUrgentText`, `TextSecondary`, `TextFaint` 등)를 그대로 쓴다 — 새 하드코딩 색상 값을 만들지 않는다.
- D-day 5단계 구간 기준(지남/알림기간이내/31일이하/100일이하/그외)은 `redesign-brief.md` 1장 표와 동일해야 한다 — 숫자를 바꾸지 않는다.
- 안드로이드 앱 코드가 바뀌는 커밋마다 `app/build.gradle.kts`의 `versionName` PATCH를 1 올리고 커밋 제목 끝에 `(vX.Y.Z)`를 붙인다(workspace CLAUDE.md "커밋마다 patch 버전 표시"). `versionCode`는 올리지 않는다(태그 시점에만).
- 실기기 화면이 "어떻게 보이는지"는 항상 사용자 확인 — Claude가 `input tap`/`uiautomator dump`/`screencap`으로 스스로 확인하지 않는다(workspace CLAUDE.md "안드로이드 앱 검증"). Claude는 빌드/설치/`logcat`/DB 값 조회만 직접 한다.
- 이 계획 문서의 범위는 `redesign-brief.md` 2.2절 중 **헤더·요약 3칸·섹션 분류·행 모양 변경**까지다. 목록/달력 전환 세그먼트(2.2-c 달력 보기)와 "+ 추가" 대상 고르기 시트(2.2-b)는 별도 후속 계획으로 분리한다(각자 독립적으로 테스트 가능한 하위 기능이라 "Scope Check" 원칙에 따라 쪼갰다) — 이번 계획에서는 헤더의 "+추가" 버튼을 눌러도 아직 아무 동작이 없는 자리만 비워둔다.

---

## Task 1: 1단계(테마·하단탭·설정화면) 실기기 확인 + 커밋

**Files:**
- 코드 변경 없음(이미 작성됨): `ui/theme/Theme.kt`, `MainActivity.kt`, `ui/home/HomeScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/navigation/Screen.kt`, `ui/asset/AssetListScreen.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `res/font/*.ttf`
- Modify: `app/build.gradle.kts` (versionName PATCH)

**Interfaces:**
- 이 태스크는 새 코드를 추가하지 않는다 — 이미 작성된 1단계 코드를 검증하고 커밋만 한다.
- Task 2 이후가 수정할 `HomeScreen.kt`의 현재 상태(1단계 결과물)를 그대로 이어받는다: `HomeRowUiModel`(Recurring/OneTime), `dDayStyle(daysUntilDue: Long, reminderLeadDays: Int): DDayStyle`, `HomeViewModel.items: StateFlow<List<HomeRowUiModel>>`.

- [ ] **Step 1: 빌드**

Run: `cd C:\github\k-home-note && .\gradlew.bat test assembleDebug`
Expected: `BUILD SUCCESSFUL` (이미 이 세션에서 한 번 성공 확인됨 — 재확인 목적).

- [ ] **Step 2: 실기기 설치 (force-stop 후 install -r)**

Run (PowerShell):
```
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am force-stop com.khomenote
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "C:\github\k-home-note\app\build\outputs\apk\debug\app-debug.apk"
```
Expected: `Success`

- [ ] **Step 3: 크래시 여부 확인 (logcat)**

Run (PowerShell):
```
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -c
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.khomenote/.MainActivity
Start-Sleep -Seconds 3
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -d *:E | Select-String -Pattern "khomenote|FATAL"
```
Expected: `libpenguin.so` 경고 외 크래시 로그 없음.

- [ ] **Step 4: 사용자에게 시각 확인 요청 (Claude가 직접 탭하지 않음)**

사용자에게 안내: "하단에 홈/모아보기/설정 탭 3개가 보이는지, 전체적으로 청록색 테마가 적용됐는지, 설정 탭에서 카테고리 관리·백업·복원이 잘 되는지 확인해주세요." 사용자 응답을 기다린다.

- [ ] **Step 5: 커밋**

Run:
```bash
cd /c/github/k-home-note
git add app/build.gradle.kts gradle/libs.versions.toml app/src/main/kotlin/com/khomenote/MainActivity.kt app/src/main/kotlin/com/khomenote/ui/theme/Theme.kt app/src/main/kotlin/com/khomenote/ui/home/HomeScreen.kt app/src/main/kotlin/com/khomenote/ui/settings/SettingsScreen.kt app/src/main/kotlin/com/khomenote/ui/navigation/Screen.kt app/src/main/kotlin/com/khomenote/ui/asset/AssetListScreen.kt app/src/main/res/font
git commit -m "feat: 생활달력 리디자인 1단계 — 청록 테마·하단 탭·설정 화면 (v0.5.9)"
git push origin master
```

---

## Task 2: D-day 구간 분류를 공유 함수로 추출 (`DueBand`)

**Files:**
- Create: `app/src/main/kotlin/com/khomenote/ui/home/HomeSummary.kt`
- Test: `app/src/test/kotlin/com/khomenote/ui/home/HomeSummaryTest.kt`

**Interfaces:**
- Consumes: 없음 (순수 함수, 외부 의존 없음).
- Produces: `enum class DueBand { OVERDUE, DUE_SOON, WITHIN_MONTH, WITHIN_3_MONTHS, LATER }`, `fun dueBandFor(daysUntilDue: Long, reminderLeadDays: Int): DueBand` — Task 3(dDayStyle 리팩터), Task 5(섹션 그룹화)가 이 함수를 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`app/src/test/kotlin/com/khomenote/ui/home/HomeSummaryTest.kt`:
```kotlin
package com.khomenote.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSummaryTest {

    @Test
    fun `dueBandFor returns OVERDUE when days until due is negative`() {
        assertEquals(DueBand.OVERDUE, dueBandFor(daysUntilDue = -1, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns DUE_SOON on the due day itself`() {
        assertEquals(DueBand.DUE_SOON, dueBandFor(daysUntilDue = 0, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns DUE_SOON when within the item's own reminder lead days`() {
        assertEquals(DueBand.DUE_SOON, dueBandFor(daysUntilDue = 7, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns WITHIN_MONTH just past the reminder lead days but within 31 days`() {
        assertEquals(DueBand.WITHIN_MONTH, dueBandFor(daysUntilDue = 8, reminderLeadDays = 7))
        assertEquals(DueBand.WITHIN_MONTH, dueBandFor(daysUntilDue = 31, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns WITHIN_3_MONTHS between 32 and 100 days`() {
        assertEquals(DueBand.WITHIN_3_MONTHS, dueBandFor(daysUntilDue = 32, reminderLeadDays = 7))
        assertEquals(DueBand.WITHIN_3_MONTHS, dueBandFor(daysUntilDue = 100, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns LATER beyond 100 days`() {
        assertEquals(DueBand.LATER, dueBandFor(daysUntilDue = 101, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor with a long reminder lead days can cover the whole WITHIN_MONTH range`() {
        // 소모품의 reminderLeadDays가 31보다 크면(예: 40일 전 알림), DUE_SOON이 WITHIN_MONTH보다 먼저 와야 한다.
        assertEquals(DueBand.DUE_SOON, dueBandFor(daysUntilDue = 40, reminderLeadDays = 40))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.khomenote.ui.home.HomeSummaryTest"`
Expected: FAIL — `DueBand`/`dueBandFor`가 아직 없어서 컴파일 에러.

- [ ] **Step 3: 최소 구현 작성**

`app/src/main/kotlin/com/khomenote/ui/home/HomeSummary.kt` (새 파일):
```kotlin
package com.khomenote.ui.home

/**
 * D-day 배지 색(dDayStyle)과 홈 화면 섹션 분류가 항상 같은 기준을 쓰도록 공유하는 구간.
 * 기준은 redesign-brief.md 1장 표(= 기존 v0.4.12 기준)와 동일하다 — 숫자를 바꾸면 이 파일만 고치면
 * 배지 색과 섹션 분류 양쪽에 다 반영된다.
 */
enum class DueBand(val sectionTitle: String) {
    OVERDUE("지났어요"),
    DUE_SOON("챙길 때"),
    WITHIN_MONTH("한 달 안"),
    WITHIN_3_MONTHS("석 달 안"),
    LATER("나중에"),
}

fun dueBandFor(daysUntilDue: Long, reminderLeadDays: Int): DueBand = when {
    daysUntilDue < 0 -> DueBand.OVERDUE
    daysUntilDue <= reminderLeadDays -> DueBand.DUE_SOON
    daysUntilDue <= 31 -> DueBand.WITHIN_MONTH
    daysUntilDue <= 100 -> DueBand.WITHIN_3_MONTHS
    else -> DueBand.LATER
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.khomenote.ui.home.HomeSummaryTest"`
Expected: PASS (7개 테스트 전부)

- [ ] **Step 5: 커밋**

```bash
cd /c/github/k-home-note
git add app/src/main/kotlin/com/khomenote/ui/home/HomeSummary.kt app/src/test/kotlin/com/khomenote/ui/home/HomeSummaryTest.kt
git commit -m "feat: D-day 구간 분류를 DueBand 공유 함수로 추출 (v0.6.0)"
```
(주의: Task 1에서 이미 versionName을 올렸으면 이번엔 그 다음 PATCH로 — 정확한 숫자는 그때 `app/build.gradle.kts`를 먼저 읽어서 확인한다. `app/build.gradle.kts`의 `versionName`을 올리는 것도 이 커밋에 포함시킨다.)

---

## Task 3: `dDayStyle`이 `dueBandFor`를 쓰도록 리팩터

**Files:**
- Modify: `app/src/main/kotlin/com/khomenote/ui/home/HomeScreen.kt` (기존 `dDayStyle` 함수)

**Interfaces:**
- Consumes: Task 2의 `dueBandFor(daysUntilDue: Long, reminderLeadDays: Int): DueBand`.
- Produces: `dDayStyle`의 동작(입출력)은 바뀌지 않는다 — 내부 구현만 `dueBandFor`를 거치도록 바뀐다.

- [ ] **Step 1: 기존 동작을 보장하는 테스트가 없으므로 먼저 추가 (리팩터 전 안전망)**

`app/src/test/kotlin/com/khomenote/ui/home/HomeSummaryTest.kt`에 이어서 추가(같은 파일, 새 테스트 함수):
```kotlin
    @Test
    fun `dueBandFor boundary matches the previously hardcoded dDayStyle thresholds`() {
        // HomeScreen.dDayStyle이 이 경계값들로 색을 나눴던 것과 정확히 같아야 한다(v0.4.12 이후 그대로).
        assertEquals(DueBand.OVERDUE, dueBandFor(-1, 7))
        assertEquals(DueBand.DUE_SOON, dueBandFor(0, 7))
        assertEquals(DueBand.DUE_SOON, dueBandFor(7, 7))
        assertEquals(DueBand.WITHIN_MONTH, dueBandFor(8, 7))
        assertEquals(DueBand.WITHIN_MONTH, dueBandFor(31, 0))
        assertEquals(DueBand.WITHIN_3_MONTHS, dueBandFor(32, 0))
        assertEquals(DueBand.WITHIN_3_MONTHS, dueBandFor(100, 0))
        assertEquals(DueBand.LATER, dueBandFor(101, 0))
    }
```

- [ ] **Step 2: 테스트 실행(이미 통과해야 함 — Task 2에서 만든 함수라 리팩터 전인데도 통과)**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.khomenote.ui.home.HomeSummaryTest"`
Expected: PASS (8개)

- [ ] **Step 3: `dDayStyle`을 `dueBandFor` 기반으로 리팩터**

`app/src/main/kotlin/com/khomenote/ui/home/HomeScreen.kt`에서 기존 `dDayStyle` 함수를 찾아 교체:
```kotlin
private fun dDayStyle(daysUntilDue: Long, reminderLeadDays: Int): DDayStyle {
    val overdueText = if (daysUntilDue == 0L) "오늘" else "D-$daysUntilDue"
    return when (dueBandFor(daysUntilDue, reminderLeadDays)) {
        DueBand.OVERDUE -> DDayStyle("D+${-daysUntilDue}", OrangeUrgent, Color.White, large = true)
        DueBand.DUE_SOON -> DDayStyle(overdueText, OrangeUrgentContainer, OrangeUrgentText, large = true)
        DueBand.WITHIN_MONTH -> DDayStyle("D-$daysUntilDue", TealContainer, TealPrimary, large = true)
        DueBand.WITHIN_3_MONTHS -> DDayStyle("D-$daysUntilDue", Color.Transparent, TextSecondary, large = false)
        DueBand.LATER -> DDayStyle("D-$daysUntilDue", Color.Transparent, TextFaint, large = false)
    }
}
```
(기존 "오늘" 특수 텍스트는 DUE_SOON 분기 안에서 `daysUntilDue == 0L` 체크로 그대로 유지한다 — 동작 변화 없음.)

- [ ] **Step 4: 전체 테스트 + 빌드**

Run: `cd C:\github\k-home-note && .\gradlew.bat test assembleDebug`
Expected: `BUILD SUCCESSFUL`, 전체 테스트 PASS.

- [ ] **Step 5: 커밋**

```bash
cd /c/github/k-home-note
git add app/src/main/kotlin/com/khomenote/ui/home/HomeScreen.kt app/src/test/kotlin/com/khomenote/ui/home/HomeSummaryTest.kt app/build.gradle.kts
git commit -m "refactor: dDayStyle이 DueBand를 공유해서 배지 색과 섹션 분류 기준을 하나로 합침 (vX.Y.Z)"
```

---

## Task 4: 요약 3칸 개수 계산 (`HomeSummaryCounts`)

**Files:**
- Modify: `app/src/main/kotlin/com/khomenote/ui/home/HomeSummary.kt`
- Test: `app/src/test/kotlin/com/khomenote/ui/home/HomeSummaryTest.kt`

**Interfaces:**
- Consumes: `HomeRowUiModel`(이미 `HomeScreen.kt`에 있음 — `daysUntilDue: Long`, `reminderLeadDays: Int` 프로퍼티 보유), Task 2의 `dueBandFor`.
- Produces: `data class HomeSummaryCounts(val overdue: Int, val dueSoon: Int, val withinMonth: Int)`, `fun List<HomeRowUiModel>.summaryCounts(): HomeSummaryCounts` — Task 7(헤더 UI)이 이 타입을 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`HomeSummaryTest.kt`에 추가(테스트 안에서 쓸 간단한 가짜 `HomeRowUiModel`이 필요한데, `HomeRowUiModel`은 sealed interface라 익명 구현이 번거롭다 — 테스트 전용 작은 구현체를 테스트 파일 안에 둔다):
```kotlin
import java.time.LocalDate

private data class FakeRow(
    override val dueDate: LocalDate,
    override val daysUntilDue: Long,
    override val reminderLeadDays: Int,
) : HomeRowUiModel {
    override val categoryId get() = 1L
    override val assetName get() = "자산"
    override val title get() = "작업"
}

class HomeSummaryCountsTest {

    @Test
    fun `summaryCounts counts each band separately`() {
        val rows = listOf(
            FakeRow(LocalDate.now().minusDays(1), -1, 7),  // OVERDUE
            FakeRow(LocalDate.now(), 0, 7),                 // DUE_SOON
            FakeRow(LocalDate.now().plusDays(5), 5, 7),      // DUE_SOON
            FakeRow(LocalDate.now().plusDays(20), 20, 7),    // WITHIN_MONTH
            FakeRow(LocalDate.now().plusDays(60), 60, 7),    // WITHIN_3_MONTHS
        )
        val counts = rows.summaryCounts()
        assertEquals(1, counts.overdue)
        assertEquals(2, counts.dueSoon)
        assertEquals(1, counts.withinMonth)
    }

    @Test
    fun `summaryCounts is all zero for an empty list`() {
        val counts = emptyList<HomeRowUiModel>().summaryCounts()
        assertEquals(HomeSummaryCounts(0, 0, 0), counts)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.khomenote.ui.home.HomeSummaryCountsTest"`
Expected: FAIL — `HomeSummaryCounts`/`summaryCounts`가 없어서 컴파일 에러.

- [ ] **Step 3: 최소 구현 작성**

`HomeSummary.kt`에 추가:
```kotlin
data class HomeSummaryCounts(val overdue: Int, val dueSoon: Int, val withinMonth: Int)

fun List<HomeRowUiModel>.summaryCounts(): HomeSummaryCounts {
    val bands = map { dueBandFor(it.daysUntilDue, it.reminderLeadDays) }
    return HomeSummaryCounts(
        overdue = bands.count { it == DueBand.OVERDUE },
        dueSoon = bands.count { it == DueBand.DUE_SOON },
        withinMonth = bands.count { it == DueBand.WITHIN_MONTH },
    )
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.khomenote.ui.home.HomeSummaryCountsTest"`
Expected: PASS (2개)

- [ ] **Step 5: 커밋**

```bash
cd /c/github/k-home-note
git add app/src/main/kotlin/com/khomenote/ui/home/HomeSummary.kt app/src/test/kotlin/com/khomenote/ui/home/HomeSummaryTest.kt
git commit -m "feat: 홈 요약 3칸(지났어요/챙길 때/한 달 안) 개수 계산 추가 (vX.Y.Z)"
```

---

## Task 5: 섹션별 그룹화 (`groupedBySection`)

**Files:**
- Modify: `app/src/main/kotlin/com/khomenote/ui/home/HomeSummary.kt`
- Test: `app/src/test/kotlin/com/khomenote/ui/home/HomeSummaryTest.kt`

**Interfaces:**
- Consumes: `List<HomeRowUiModel>`, `dueBandFor`.
- Produces: `fun <T : HomeRowUiModel> List<T>.groupedBySection(): List<Pair<DueBand, List<T>>>` — 각 그룹 내부는 `dueDate` 오름차순 유지, 그룹 순서는 `DueBand.entries` 순서(지났어요→챙길 때→한 달 안→석 달 안→나중에), 항목이 없는 구간은 결과에서 빠진다. Task 8(리스트 렌더링)이 이 함수를 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`HomeSummaryTest.kt`에 추가:
```kotlin
class HomeGroupedBySectionTest {

    @Test
    fun `groupedBySection orders groups overdue first and skips empty bands`() {
        val overdue = FakeRow(LocalDate.now().minusDays(1), -1, 7)
        val withinMonth = FakeRow(LocalDate.now().plusDays(20), 20, 7)
        val rows = listOf(withinMonth, overdue) // 입력 순서가 뒤섞여 있어도

        val grouped = rows.groupedBySection()

        assertEquals(listOf(DueBand.OVERDUE, DueBand.WITHIN_MONTH), grouped.map { it.first })
        assertEquals(listOf(overdue), grouped[0].second)
        assertEquals(listOf(withinMonth), grouped[1].second)
    }

    @Test
    fun `groupedBySection keeps dueDate ascending order within a group`() {
        val later = FakeRow(LocalDate.now().plusDays(6), 6, 7)
        val sooner = FakeRow(LocalDate.now().plusDays(1), 1, 7)
        val grouped = listOf(later, sooner).groupedBySection()

        assertEquals(1, grouped.size)
        assertEquals(listOf(sooner, later), grouped.first().second)
    }

    @Test
    fun `groupedBySection returns an empty list for an empty input`() {
        assertEquals(emptyList<Pair<DueBand, List<HomeRowUiModel>>>(), emptyList<HomeRowUiModel>().groupedBySection())
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.khomenote.ui.home.HomeGroupedBySectionTest"`
Expected: FAIL — `groupedBySection`이 없어서 컴파일 에러.

- [ ] **Step 3: 최소 구현 작성**

`HomeSummary.kt`에 추가:
```kotlin
fun <T : HomeRowUiModel> List<T>.groupedBySection(): List<Pair<DueBand, List<T>>> {
    val byBand = groupBy { dueBandFor(it.daysUntilDue, it.reminderLeadDays) }
    return DueBand.entries.mapNotNull { band ->
        val items = byBand[band]?.sortedBy { it.dueDate } ?: return@mapNotNull null
        if (items.isEmpty()) null else band to items
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.khomenote.ui.home.HomeGroupedBySectionTest"`
Expected: PASS (3개)

- [ ] **Step 5: 커밋**

```bash
cd /c/github/k-home-note
git add app/src/main/kotlin/com/khomenote/ui/home/HomeSummary.kt app/src/test/kotlin/com/khomenote/ui/home/HomeSummaryTest.kt
git commit -m "feat: 홈 목록을 D-day 구간별 섹션으로 그룹화하는 groupedBySection 추가 (vX.Y.Z)"
```

---

## Task 6: 행 둘째 줄 주기 설명 문구 (`formatRecurringSubtitle`/`formatOneTimeSubtitle`)

**Files:**
- Modify: `app/src/main/kotlin/com/khomenote/ui/home/HomeSummary.kt`
- Test: `app/src/test/kotlin/com/khomenote/ui/home/HomeSummaryTest.kt`

**Interfaces:**
- Consumes: `com.khomenote.data.local.entity.ConsumableItemEntity`(기존 필드: `cycleDays: Int`, `monthlyDueDay: Int?`, `yearlyDueMonth: Int?`, `yearlyDueDay: Int?`, `isYearlyLunar: Boolean?`), `com.khomenote.data.local.entity.RepairRecordEntity`(기존 필드: `date: LocalDate`, `memo: String?`).
- Produces: `fun formatRecurringSubtitle(item: ConsumableItemEntity, latestRecordDate: LocalDate?): String`, `fun formatOneTimeSubtitle(record: RepairRecordEntity): String` — Task 8(행 렌더링)이 이 두 함수를 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`HomeSummaryTest.kt`에 추가:
```kotlin
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.RepairRecordEntity

class HomeSubtitleTest {

    @Test
    fun `formatRecurringSubtitle for monthly fixed day matches AssetDetailScreen wording`() {
        val item = ConsumableItemEntity(assetId = 1, name = "카드값", cycleDays = 30, monthlyDueDay = 25)
        assertEquals("매달 25일", formatRecurringSubtitle(item, latestRecordDate = null))
    }

    @Test
    fun `formatRecurringSubtitle for yearly fixed day includes lunar suffix when set`() {
        val item = ConsumableItemEntity(
            assetId = 1, name = "생일", cycleDays = 30,
            yearlyDueMonth = 11, yearlyDueDay = 14, isYearlyLunar = true,
        )
        assertEquals("매년 11월 14일(음력)", formatRecurringSubtitle(item, latestRecordDate = null))
    }

    @Test
    fun `formatRecurringSubtitle for interval mode shows the cycle in Korean units`() {
        val item = ConsumableItemEntity(assetId = 1, name = "엔진오일", cycleDays = 180)
        assertEquals("6개월마다", formatRecurringSubtitle(item, latestRecordDate = null))
    }

    @Test
    fun `formatRecurringSubtitle for interval mode appends the last done date when it exists`() {
        val item = ConsumableItemEntity(assetId = 1, name = "엔진오일", cycleDays = 180)
        val result = formatRecurringSubtitle(item, latestRecordDate = LocalDate.of(2026, 4, 30))
        assertEquals("6개월마다 · 지난번 4월 30일", result)
    }

    @Test
    fun `formatRecurringSubtitle for an interval that does not divide evenly falls back to days`() {
        val item = ConsumableItemEntity(assetId = 1, name = "소모품", cycleDays = 40)
        assertEquals("40일마다", formatRecurringSubtitle(item, latestRecordDate = null))
    }

    @Test
    fun `formatOneTimeSubtitle shows the date and a memo preview`() {
        val record = RepairRecordEntity(assetId = 1, name = "보일러 점검", date = LocalDate.of(2026, 10, 20), memo = "전 담당자에게 확인")
        assertEquals("10월 20일 · 전 담당자에게 확인", formatOneTimeSubtitle(record))
    }

    @Test
    fun `formatOneTimeSubtitle shows only the date when there is no memo`() {
        val record = RepairRecordEntity(assetId = 1, name = "보일러 점검", date = LocalDate.of(2026, 10, 20))
        assertEquals("10월 20일", formatOneTimeSubtitle(record))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.khomenote.ui.home.HomeSubtitleTest"`
Expected: FAIL — `formatRecurringSubtitle`/`formatOneTimeSubtitle`이 없어서 컴파일 에러.

- [ ] **Step 3: 최소 구현 작성**

`HomeSummary.kt`에 추가(상단에 import 필요: `com.khomenote.data.local.entity.ConsumableItemEntity`, `com.khomenote.data.local.entity.RepairRecordEntity`, `java.time.LocalDate`, `java.time.format.DateTimeFormatter`):
```kotlin
private val KOREAN_MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")

/**
 * 홈 목록 행의 둘째 줄에 쓰는 주기 설명. AssetDetailScreen.formatCycle()은 "1yr 3m"처럼 자산
 * 상세 화면에서 소모품 이름 옆에 붙이는 짧은 영문 축약형이라, 여기서는 완전한 한국어 문장으로
 * 새로 만든다(둘은 쓰이는 화면이 달라서 같은 함수를 억지로 재사용하지 않는다).
 */
fun formatRecurringSubtitle(item: ConsumableItemEntity, latestRecordDate: LocalDate?): String {
    item.monthlyDueDay?.let { return "매달 ${it}일" }
    item.yearlyDueMonth?.let { month ->
        val lunarSuffix = if (item.isYearlyLunar == true) "(음력)" else ""
        return "매년 ${month}월 ${item.yearlyDueDay}일$lunarSuffix"
    }
    val intervalLabel = formatIntervalKorean(item.cycleDays)
    val lastDoneLabel = latestRecordDate?.let { " · 지난번 ${it.format(KOREAN_MONTH_DAY)}" } ?: ""
    return "$intervalLabel$lastDoneLabel"
}

// cycleDays를 "6개월마다"/"1년마다"/"40일마다"처럼 가장 큰 단위 하나로 표현한다.
// 365로 안 나누어지는 애매한 값(예: 400일)은 "1년 1개월마다"처럼 두 단위까지만 보여준다.
internal fun formatIntervalKorean(cycleDays: Int): String {
    val years = cycleDays / 365
    val remAfterYears = cycleDays % 365
    val months = remAfterYears / 30
    val days = remAfterYears % 30
    val parts = buildList {
        if (years > 0) add("${years}년")
        if (months > 0) add("${months}개월")
        if (years == 0 && months == 0) add("${days}일")
    }
    return "${parts.joinToString(" ")}마다"
}

fun formatOneTimeSubtitle(record: RepairRecordEntity): String {
    val dateLabel = record.date.format(KOREAN_MONTH_DAY)
    val memoPreview = record.memo?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
    return "$dateLabel$memoPreview"
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.khomenote.ui.home.HomeSubtitleTest"`
Expected: PASS (7개)

- [ ] **Step 5: 커밋**

```bash
cd /c/github/k-home-note
git add app/src/main/kotlin/com/khomenote/ui/home/HomeSummary.kt app/src/test/kotlin/com/khomenote/ui/home/HomeSummaryTest.kt
git commit -m "feat: 홈 목록 행 둘째 줄에 쓸 주기/일정 설명 문구 함수 추가 (vX.Y.Z)"
```

---

## Task 7: 청록 헤더 + 요약 3칸 UI

**Files:**
- Modify: `app/src/main/kotlin/com/khomenote/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: Task 4의 `HomeSummaryCounts`/`summaryCounts()`, `ui/theme/Theme.kt`의 `TealPrimary`, `TealHeaderMuted`, `OrangeHeaderDot`.
- Produces: `@Composable private fun HomeHeader(summary: HomeSummaryCounts, onAddClick: () -> Unit)` — `HomeScreen`의 `Scaffold.topBar`에서 호출한다. 이 태스크에서는 `onAddClick`을 받아두기만 하고 실제 동작은 비워둔다(Toast 등으로 "다음 단계에서 추가" 표시) — "+ 추가 대상 고르기 시트"는 범위 밖(이 문서 상단 Global Constraints 참고).

- [ ] **Step 1: 헤더 컴포저블 작성**

`HomeScreen.kt`의 `dDayStyle` 함수 바로 아래에 추가(새 import 필요: `androidx.compose.foundation.layout.Spacer`—이미 있음, `androidx.compose.foundation.shape.RoundedCornerShape`—이미 있음, `com.khomenote.ui.theme.TealHeaderMuted`, `com.khomenote.ui.theme.OrangeHeaderDot`, `java.time.format.DateTimeFormatter`, `java.time.format.TextStyle`, `java.util.Locale`):
```kotlin
private val HEADER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")

@Composable
private fun HomeHeader(summary: HomeSummaryCounts, onAddClick: () -> Unit) {
    val today = remember { LocalDate.now() }
    val weekday = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TealPrimary, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("생활달력", style = MaterialTheme.typography.labelSmall, color = TealHeaderMuted)
                Text(
                    "${today.format(HEADER_DATE_FORMAT)} $weekday",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
            TextButton(onClick = onAddClick) { Text("+ 추가", color = Color.White) }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            HomeSummaryTile("지났어요", summary.overdue, showDot = summary.overdue > 0)
            HomeSummaryDivider()
            HomeSummaryTile("챙길 때", summary.dueSoon)
            HomeSummaryDivider()
            HomeSummaryTile("한 달 안", summary.withinMonth)
        }
    }
}

@Composable
private fun HomeSummaryTile(label: String, count: Int, showDot: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(OrangeHeaderDot, shape = androidx.compose.foundation.shape.CircleShape),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                "$count",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = com.khomenote.ui.theme.NumberFontFamily,
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = TealHeaderMuted)
    }
}

@Composable
private fun HomeSummaryDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(32.dp)
            .background(TealHeaderMuted),
    )
}
```

- [ ] **Step 2: `HomeScreen`의 `topBar`를 `HomeHeader`로 교체**

`HomeScreen.kt`에서 `Scaffold(topBar = { TopAppBar(title = { Text("K-Home-Note") }) }, ...)`를 찾아 교체:
```kotlin
Scaffold(
    topBar = {
        HomeHeader(
            summary = items.summaryCounts(),
            onAddClick = { /* TODO: 대상 고르기 시트 — 후속 계획(2.2-b)에서 구현 */ },
        )
    },
) { padding ->
```

- [ ] **Step 3: 필요한 import 추가**

`HomeScreen.kt` 상단에 추가(이미 있는 건 건너뛴다): `androidx.compose.foundation.layout.size`, `androidx.compose.foundation.layout.width`(이미 있음), `androidx.compose.foundation.shape.CircleShape`, `com.khomenote.ui.theme.TealHeaderMuted`, `com.khomenote.ui.theme.OrangeHeaderDot`, `java.time.format.DateTimeFormatter`, `java.time.format.TextStyle`, `java.util.Locale`.

- [ ] **Step 4: 빌드**

Run: `cd C:\github\k-home-note && .\gradlew.bat test assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 설치 + 실기기 확인 요청**

Run (PowerShell, force-stop 후 install -r):
```
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am force-stop com.khomenote
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "C:\github\k-home-note\app\build\outputs\apk\debug\app-debug.apk"
```
사용자에게 안내: "홈 화면 맨 위에 청록색 헤더 띠와 '지났어요/챙길 때/한 달 안' 숫자 3칸이 보이는지 확인해주세요. '+ 추가'는 아직 안 눌리는 자리만 있는 상태예요."

- [ ] **Step 6: 커밋**

```bash
cd /c/github/k-home-note
git add app/src/main/kotlin/com/khomenote/ui/home/HomeScreen.kt app/build.gradle.kts
git commit -m "feat: 홈 화면에 청록 헤더 + 요약 3칸(지났어요/챙길 때/한 달 안) 추가 (vX.Y.Z)"
```

---

## Task 8: 목록을 섹션 머리로 나누고 행 둘째 줄을 주기 설명으로 교체

**Files:**
- Modify: `app/src/main/kotlin/com/khomenote/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: Task 5의 `groupedBySection()`, Task 6의 `formatRecurringSubtitle`/`formatOneTimeSubtitle`.
- Produces: 없음(화면 렌더링 최종 결과물).

- [ ] **Step 1: `LazyColumn` 내용을 섹션별로 재구성**

`HomeScreen.kt`에서 기존 `LazyColumn(state = listState, ...) { items(filteredItems, key = {...}) { uiModel -> ... } }` 블록을 찾아, `items(...)` 호출을 `groupedBySection()` 결과를 순회하는 형태로 교체한다:
```kotlin
LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
    filteredItems.groupedBySection().forEach { (band, rowsInBand) ->
        item(key = "section-${band.name}") {
            Text(
                band.sectionTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TealPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(
            rowsInBand,
            key = {
                when (it) {
                    is HomeRowUiModel.Recurring -> "recurring-${it.status.item.id}"
                    is HomeRowUiModel.OneTime -> "onetime-${it.record.record.id}"
                }
            },
        ) { uiModel ->
            // 기존 Row { ... } 내용 그대로 — 아래 Step 2에서 subtitle 줄만 바뀐다.
        }
    }
}
```
(`item(key=...)`을 쓰려면 `androidx.compose.foundation.lazy.item` import가 추가로 필요하다 — `items`는 이미 import돼 있다.)

- [ ] **Step 2: 행의 카테고리 줄을 빼고 주기 설명 줄로 교체**

같은 파일에서 각 행의 `Column(modifier = Modifier.weight(1f)) { ... }` 안쪽(카테고리 이모지 `Text` + 자산·작업명 `Row`)을 찾아, 카테고리 `Text`를 지우고 그 자리에 주기 설명 `Text`를 추가한다:
```kotlin
Column(modifier = Modifier.weight(1f)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${uiModel.assetName} · ${uiModel.title}",
            fontWeight = FontWeight.Bold,
        )
        if (uiModel is HomeRowUiModel.OneTime) {
            Spacer(Modifier.width(6.dp))
            Text(
                "한 번",
                style = MaterialTheme.typography.labelSmall,
                color = TealPrimary,
                modifier = Modifier
                    .border(1.dp, OnceTagBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
    val subtitle = when (uiModel) {
        is HomeRowUiModel.Recurring -> formatRecurringSubtitle(uiModel.status.item, uiModel.status.latestRecordDate)
        is HomeRowUiModel.OneTime -> formatOneTimeSubtitle(uiModel.record.record)
    }
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
}
```
(이제 `categoryById`/`category` 지역 변수는 이 블록에서 안 쓰이지만, 카테고리 필터 칩에서는 여전히 쓰이므로 `HomeScreen` 함수 전체에서는 지우지 않는다 — 이 Row 안에서만 참조를 뺀다.)

- [ ] **Step 3: 이제 쓸모없어진 안내 문구 삭제**

"예정일순으로 정렬되어있습니다..." `Text`(섹션 머리가 이 역할을 대신함)를 삭제한다.

- [ ] **Step 4: 빌드**

Run: `cd C:\github\k-home-note && .\gradlew.bat test assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 설치 + 실기기 확인 요청**

(force-stop 후 install -r, Task 1 Step 2와 동일한 명령)
사용자에게 안내: "목록이 '지났어요/챙길 때/한 달 안/석 달 안/나중에' 제목으로 나뉘어 보이는지, 각 항목 둘째 줄에 '6개월마다 · 지난번 4월 30일' 같은 주기 설명이 카테고리 이름 대신 나오는지 확인해주세요."

- [ ] **Step 6: 커밋 + 푸시**

```bash
cd /c/github/k-home-note
git add app/src/main/kotlin/com/khomenote/ui/home/HomeScreen.kt app/build.gradle.kts
git commit -m "feat: 홈 목록을 D-day 구간별 섹션으로 나누고 행 둘째 줄을 주기 설명으로 교체 (vX.Y.Z)"
git push origin master
```

---

## Self-Review Notes (writing-plans 체크리스트, 작성자가 직접 확인함)

1. **spec coverage**: redesign-brief.md 2.2절 중 "청록 헤더"(Task 7), "요약 3칸"(Task 4+7), "섹션별 그룹화"(Task 5+8), "행 모양(주기 설명, 카테고리 줄 삭제)"(Task 6+8), "MoreBelowIndicator 유지"(手を付けない=유지)까지 커버함. "목록/달력 전환"(2.2-c)과 "+ 추가 대상 고르기 시트"(2.2-b)는 Global Constraints에 명시한 대로 범위 밖(후속 계획).
2. **placeholder scan**: 전체 코드 블록에 TBD/TODO성 placeholder 없음(Task 7의 `onAddClick` 내부 주석 하나만 "후속 계획에서 구현"이라고 명시적으로 범위 밖임을 표시한 것 — 이는 계획의 Global Constraints에서 이미 공식적으로 범위 제외를 선언했으므로 placeholder가 아니라 의도된 동작임).
3. **type consistency**: `DueBand`(Task 2) → `dDayStyle`(Task 3), `summaryCounts()`(Task 4) → `HomeHeader`(Task 7), `groupedBySection()`(Task 5) → `LazyColumn`(Task 8), `formatRecurringSubtitle`/`formatOneTimeSubtitle`(Task 6) → 행 렌더링(Task 8) 모두 함수 시그니처가 태스크 간에 일치함.
