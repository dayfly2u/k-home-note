# 홈/자동차 소모품 정비 노트 앱 — 설계 문서

- 날짜: 2026-08-15
- 프로젝트: `k-home-note` (`C:\github\k-home-note`)
- 패키지명: `com.khomenote`
- 참고: mycar.naver.com/maintenance/items (직접 접속은 브라우저 안전 정책으로 차단되어, 일반적인 "차량 소모품 관리" 앱 패턴을 기준으로 설계함)

## 목적

집안 가전제품(청소기, 정수기, 공기청정기 등)과 자동차의 소모품 교체 주기를 관리한다. 교체 시기가 다가오면 알림으로 미리 알려주고, 교체/구매 이력을 기록으로 남긴다.

## 범위

- Android 앱 (단일 사용자, 기기 내 로컬 저장)
- 가전/자동차 소모품을 하나의 앱에서 통합 관리
- 클라우드 동기화/백업은 이번 범위에 포함하지 않음 (추후 필요 시 별도 설계)

## 데이터 구조

### Asset (자산)
등록하는 제품/차량 단위.

| 필드 | 설명 |
|---|---|
| id | PK |
| name | 예: "다이슨 V15", "아반떼 하이브리드" |
| category | 가전 / 자동차 |
| memo | 자유 텍스트 |
| createdAt | 등록일 |

### ConsumableItem (소모품 항목)
Asset에 딸린 소모품. Asset 1 : N ConsumableItem.

| 필드 | 설명 |
|---|---|
| id | PK |
| assetId | FK → Asset |
| name | 예: "필터", "엔진오일" |
| cycleDays | 교체 주기 (일 단위로 통일 저장; 입력 UI에서는 일/개월/년 단위로 받아 내부적으로 일수로 환산) |
| reminderLeadDays | 알림을 며칠 전에 줄지 (기본값 7, 항목별 수정 가능) |
| memo | 자유 텍스트 |
| createdAt | 항목 등록일 (이력이 하나도 없을 때 다음 예정일 계산의 기준값으로 사용) |

### ReplacementRecord (교체/구매 이력)
ConsumableItem에 딸린 이력. ConsumableItem 1 : N ReplacementRecord.

| 필드 | 설명 |
|---|---|
| id | PK |
| consumableItemId | FK → ConsumableItem |
| date | 교체/구매일 |
| price | 가격 (nullable) |
| vendor | 구매처/링크 (nullable) |
| photoUri | 영수증/제품 사진 경로 (nullable, 앱 내부 저장소에 복사된 파일 경로) |
| memo | 자유 텍스트 (nullable) |

### 다음 교체 예정일 계산

```
가장 최근 ReplacementRecord.date가 있으면:
    다음예정일 = 최신 ReplacementRecord.date + cycleDays
없으면:
    다음예정일 = ConsumableItem.createdAt + cycleDays
```

"교체 완료" 액션은 새 ReplacementRecord를 추가하는 것과 동일하며, 자동으로 다음 예정일이 갱신된다.

## 화면 구성

1. **홈 (대시보드)**: 전체 소모품을 D-day 임박순으로 정렬. 연체 항목은 빨간색으로 상단 고정. 카테고리 필터(전체/가전/자동차) 탭. 리스트에서 "교체 완료" 바로가기 가능.
2. **자산 관리**: 자산 목록(+ 추가/수정/삭제) → 자산 탭 시 그 자산의 소모품 목록.
3. **소모품 항목 상세**: 이름/주기/다음예정일/알림 리드타임, 이력 리스트(최신순), "교체 완료" 버튼(오늘 날짜 기본값의 이력 입력 폼: 가격/구매처/사진/메모).
4. **소모품 항목 추가**: 자산 선택 → 이름/주기(일·개월·년 입력 후 일수로 환산)/알림 리드타임.

## 알림

- `WorkManager`의 `PeriodicWorkRequest`로 하루 1회 전체 `ConsumableItem`을 스캔.
- 스캔 시점에 `다음예정일 - 오늘 == reminderLeadDays` 이거나 `다음예정일 == 오늘`인 항목에 대해 알림 발송.
- 알림 탭 시 해당 소모품 상세 화면으로 이동 (딥링크).
- 정확한 시각 보장이 필요 없는 성격(그날 하루 안에만 알림이 오면 충분)이므로 `AlarmManager` 기반 정확한 알람(및 관련 권한 처리)은 사용하지 않는다.

## 기술 스택

- Kotlin, Jetpack Compose, Material3
- Room + KSP (로컬 DB)
- WorkManager (알림 스케줄링)
- 사진: 카메라/갤러리에서 선택 → 앱 내부 저장소로 복사 후 상대경로를 DB에 저장 (외부 URI 권한 만료 문제 방지)
- minSdk 26, compileSdk/targetSdk 36
- golf-round-tracker 프로젝트와 동일한 빌드 구성 패턴(Gradle Kotlin DSL, `libs.versions.toml`) 사용

## 테스트 계획

- Room DAO 단위 테스트: 다음예정일 계산, 이력 추가 시 갱신 로직
- 알림 대상 판정 로직 단위 테스트: 특정 "오늘" 날짜 기준으로 알림 대상 항목이 올바르게 선정되는지
- UI 동작(화면 렌더링, 탭 흐름 등 눈으로 봐야 하는 것)은 실기기에서 사용자가 직접 확인 (워크스페이스 규칙: adb로 화면을 넘겨가며 확인하지 않음)

## 비범위 (이번에 하지 않는 것)

- 클라우드 백업/동기화, 다중 기기 지원
- 자동차 주행거리(km) 기반 계산 (날짜 기준으로만 관리)
- 소모품 프리셋(엔진오일/필터 등 미리 정의된 템플릿) — 추후 필요 시 추가 검토
