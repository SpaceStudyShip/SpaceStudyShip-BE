# 행성 탐험 — 프론트 통합 요구사항 (Frontend → Backend API 요청)

> **작성:** 2026-05-29
> **대상 기능:** Exploration (행성/지역 탐험)
> **성격:** Flutter(프론트)가 백엔드에 요구하는 API 계약 명세. 백엔드 `docs/api-specs/05_exploration.md`와 대조·정합을 맞추기 위한 문서.
> **관련 코드:** `lib/features/exploration/`

---

## 1. 목적 & 범위

### 목적

Flutter 앱의 탐험 기능을 **게스트 로컬 모드 → 회원 서버 연동**으로 전환하기 위해, 프론트가 소비할 API 계약을 프론트 관점에서 정의한다. 현재 프론트는 `ExplorationLocalRepositoryImpl`(SharedPreferences + 시드 데이터)만 구현돼 있고, 회원용 `ExplorationRemoteRepositoryImpl`은 미구현 상태다. 이 문서는 그 Remote 구현의 입력 계약이 된다.

### 범위 (In scope)

- 회원(소셜 로그인, JWT 인증) 사용자가 사용하는 서버 API 계약
- 프론트가 렌더링·상태표시에 필요한 데이터 필드 명세
- 해금 동작의 요청/응답/에러 계약

### 비범위 (Out of scope) — 명시적 제외

- **게스트 데이터 마이그레이션 없음.** 게스트는 100% 로컬(SharedPreferences) 전용이다. 로그인/회원전환 시 게스트의 로컬 진행도를 서버로 올리는 동기화·병합 로직은 **요구하지 않는다.** 게스트 데이터는 삭제 시 그대로 소멸한다.
- 따라서 "guest progress → server sync" 같은 별도 엔드포인트는 불필요하다.

---

## 2. 인증 & 게스트/회원 경계

| 구분 | 데이터 소스 | 인증 | 비고 |
|------|------------|------|------|
| 게스트 | 로컬 (SharedPreferences) | 없음 | 순수 로컬, 서버 호출 없음, 마이그레이션 없음 |
| 회원 | 서버 API (`/api/explorations/**`) | JWT 필요 | 이 문서가 정의하는 API |

- 이 API의 모든 엔드포인트는 **JWT 인증 필수**다.
- 게스트와 회원의 진행 상태는 완전히 분리된다. 서로 연동되지 않는다.

---

## 3. 프론트가 소비할 데이터 모델

프론트는 응답 노드를 `ExplorationNodeEntity`로 매핑한다. 아래는 프론트가 **렌더링·상태판정에 실제로 사용하는** 필드와 요구 사항이다.

### 3.1 탐험 노드 필드

| 필드 | 타입 | Nullable | 프론트 용도 | 현재 프론트 entity 상태 |
|------|------|----------|------------|------------------------|
| `id` | String | X | 노드 식별, 해금 API 호출 키 | 있음 |
| `name` | String | X | 노드 이름 표시 | 있음 |
| `nodeType` | String (`planet`/`region`) | X | planet/region 분기 렌더링 | 있음 (enum: galaxy/starSystem/planet/region) |
| `depth` | Integer | X | 계층 깊이 (planet=2, region=3) | 있음 |
| `icon` | String | X | 아이콘 렌더링 (6번 섹션 참조) | 있음 |
| `parentId` | String | O | 지역의 상위 행성 (planet은 null) | 있음 |
| `prerequisiteId` | String | O | **선행 행성 게이트 표시** (planet만, region은 null) | **없음 — 추가 필요** |
| `requiredFuel` | Integer | X | 해금 비용 표시 (0이면 기본 해금) | 있음 |
| `isUnlocked` | Boolean | X | 잠김/해금 UI 상태 | 있음 |
| `isCleared` | Boolean | X | 클리어 배지·진행 표시 | 있음 |
| `sortOrder` | Integer | X | 표시 순서 정렬 | 있음 |
| `description` | String | X | 노드 설명 표시 | 있음 |
| `mapX` | Double | X | 맵상 가로 위치 (0.0~1.0) | 있음 |
| `mapY` | Double | X | 맵상 세로 위치 (0.0~1.0) | 있음 |
| `unlockedAt` | String(ISO8601) | O | 해금 시각 (null=미해금) | 있음 (DateTime?) |

> **요구:** 백엔드 노드 응답은 위 필드를 모두 포함해야 한다. 특히 `prerequisiteId`는 프론트 entity에 아직 없으므로 추가 작업 대상이며, 응답 스키마에 반드시 포함돼야 한다.

### 3.2 진행도(progress) 객체 — 행성 목록 응답에 포함

프론트 `ExplorationProgressEntity`와 매핑된다.

| 필드 | 타입 | 프론트 용도 |
|------|------|------------|
| `clearedChildren` | Integer | 진행 바 "n / m" 표시 |
| `totalChildren` | Integer | 진행 바 분모 |
| `progressRatio` | Double (0.0~1.0) | 진행 바 비율 (프론트도 계산 가능, 서버 제공 시 그대로 사용) |

> **요구:** 행성 목록 응답의 각 행성에 `progress` 객체가 포함돼야 한다. (프론트는 행성별 하위 지역 클리어 수를 별도 호출 없이 목록에서 바로 표시하고 싶음.)

---

## 4. 필요 엔드포인트 (프론트 관점 계약)

Base Path: `/api/explorations`

| # | Method | Path | 프론트 호출 시점 |
|---|--------|------|-----------------|
| 1 | GET | `/planets` | 탐험 화면 진입 시 (행성 맵 렌더링) |
| 2 | GET | `/planets/{planetId}/regions` | 행성 상세 진입 시 (지역 목록 렌더링) |
| 3 | POST | `/regions/{regionId}/unlock` | 지역 해금 버튼 탭 |
| 4 | POST | `/planets/{planetId}/unlock` | 행성 해금 버튼 탭 |

### 4.1 GET `/planets` — 행성 목록

- **호출 시점:** 탐험 메인 화면 진입, 해금 직후 갱신
- **응답:** 전체 행성 배열. 각 행성은 3.1 필드 + 3.2 `progress` 포함. `sortOrder` 오름차순.
- **프론트 반영:** 행성 노드를 `mapX/mapY`로 맵에 배치, 잠김/해금/클리어 상태로 스타일 분기, 진행 바 표시.

### 4.2 GET `/planets/{planetId}/regions` — 지역 목록

- **호출 시점:** 특정 행성 상세 진입
- **응답:** 해당 행성 하위 지역 배열 (3.1 필드, region은 `prerequisiteId=null`). `sortOrder` 오름차순.
- **프론트 반영:** 지역 카드/노드 목록, 해금 비용·상태 표시.

### 4.3 POST `/regions/{regionId}/unlock` — 지역 해금

- **요청:** Body 없음. Path에 `regionId`.
- **기대 응답(200):**
  ```json
  {
    "region": { "id": "...", "name": "...", "isUnlocked": true, "isCleared": true, "unlockedAt": "..." },
    "fuelConsumed": 4,
    "currentFuel": 250,
    "planetCleared": false
  }
  ```
- **프론트 반영:**
  - `currentFuel`로 연료 게이지 즉시 갱신 (별도 fuel 조회 불필요)
  - `region.isUnlocked/isCleared`로 해당 지역 상태 갱신
  - `planetCleared=true`면 상위 행성 클리어 연출 트리거
- **요구:** 연료 차감은 서버에서 원자적으로 처리. 프론트는 별도 fuel consume API를 호출하지 않는다.

### 4.4 POST `/planets/{planetId}/unlock` — 행성 해금

- **요청:** Body 없음. Path에 `planetId`.
- **기대 응답(200):**
  ```json
  {
    "planet": { "id": "...", "name": "...", "isUnlocked": true, "isCleared": false, "unlockedAt": "..." },
    "fuelConsumed": 12,
    "currentFuel": 50
  }
  ```
- **프론트 반영:** `currentFuel` 게이지 갱신, 행성 잠김 해제 연출.

---

## 5. 에러 / 엣지케이스 계약 요청

프론트는 아래 상황별로 **사용자에게 다른 메시지/처리**를 보여줘야 하므로, 서버는 식별 가능한 `code`를 반환해야 한다. (공통 에러 포맷은 `00_common.md` 기준)

| 엔드포인트 | Status | code | 프론트 처리 |
|-----------|--------|------|------------|
| 지역 해금 | 400 | `INSUFFICIENT_FUEL` | "연료가 부족해요" + 필요/보유 연료 안내 |
| 지역 해금 | 400 | `ALREADY_UNLOCKED` | 이미 해금됨 — 무음 처리 또는 상태 재동기화 |
| 지역 해금 | 400 | `PLANET_LOCKED` | "먼저 행성을 해금해야 해요" |
| 지역 해금 | 404 | `REGION_NOT_FOUND` | 데이터 오류 안내 + 목록 새로고침 |
| 행성 해금 | 400 | `INSUFFICIENT_FUEL` | "연료가 부족해요" |
| 행성 해금 | 400 | `ALREADY_UNLOCKED` | 이미 해금됨 — 무음/재동기화 |
| 행성 해금 | 400 | `PREREQUISITE_NOT_CLEARED` | "선행 행성을 먼저 클리어해야 해요" |
| 행성 해금 | 404 | `PLANET_NOT_FOUND` | 데이터 오류 안내 + 목록 새로고침 |
| 지역 목록 | 404 | `PLANET_NOT_FOUND` | 데이터 오류 안내 |

> **요구:**
> - `INSUFFICIENT_FUEL` 응답에는 가능하면 `requiredFuel`, `currentFuel`을 함께 담아 프론트가 정확한 안내 문구를 만들 수 있게 해줄 것.
> - 에러 응답 본문 스키마(`code`, `message` 키)를 `00_common.md`와 일치시킬 것.

---

## 6. 필드 정합성 요청 (icon / 좌표 / prerequisiteId)

### 6.1 `icon` 값 규칙 — 프론트 렌더링 의존

프론트는 `icon` 값으로 두 가지 렌더링 분기를 이미 구현해 두었다:

- **행성 / 비(非)지구 지역:** 행성 이름 식별자 사용 — `earth`, `mercury`, `venus`, `mars`, `jupiter`, `saturn`, `uranus`, `neptune`
- **지구 하위 지역:** ISO 3166-1 alpha-2 **국가 코드** 사용 — `KR`, `JP`, `TH`, `CN`, `IN`, `GB`, `FR`, `CA`, `US`, `BR`, `AU`, `EG` (국기 아이콘 렌더링)

> **요구:** 서버 시드의 `icon` 값은 위 어휘(vocabulary)를 벗어나지 않아야 한다. 프론트가 모르는 `icon` 값이 오면 렌더링 폴백 처리만 가능하다. 새 노드 추가 시 icon 값 규칙을 프론트와 합의할 것.

### 6.2 좌표 체계 `mapX` / `mapY`

- 둘 다 `0.0 ~ 1.0` 정규화 비율. 프론트가 화면 크기에 곱해 배치한다.
- **요구:** 모든 행성 노드는 화면 안에 들어오는 좌표를 가져야 한다(겹침 최소화). 지역 노드 좌표는 현재 프론트에서 필수 사용은 아니지만, 응답에는 포함할 것(기본값 허용).

### 6.3 `prerequisiteId` 추가

- 프론트 entity에 아직 없음. 백엔드가 선행 행성 게이트를 구현한다면 프론트도 entity·UI에 추가해야 한다.
- **요구:** 행성 노드 응답에 `prerequisiteId`(없으면 null) 포함. 이 값으로 프론트는 "선행 행성 클리어 필요" 잠금 사유를 표시한다.

---

## 7. 동기화 Tier

- 탐험 해금은 **Tier 2 (Server-Validated)**: 연료 잔량 확인·차감·해금이 서버에서 원자적으로 처리된다. 해금 동작은 **온라인 필수.**
- 행성/지역 목록 조회는 응답을 로컬에 **읽기 캐시**로 저장. 오프라인 시 캐시를 표시하되 "오프라인" 상태를 노출한다.
- **요구:** 해금 API는 오프라인에서 호출 불가하므로, 네트워크 실패 시 프론트가 명확히 구분할 수 있는 에러(타임아웃/네트워크)를 반환할 것. 부분 성공(연료만 차감되고 해금 실패 등)이 없도록 트랜잭션 보장.

---

## 8. 현행 vs 요구 갭 체크리스트

프론트 현행 코드/구버전 spec과 신버전 백엔드 spec(`05_exploration.md`) 사이의 차이. **백엔드(본인)가 확정·정합을 맞춰야 할 항목.**

### 8.1 데이터 모델 갭

- [ ] 프론트 `ExplorationNodeEntity`에 `prerequisiteId` 필드 추가 (현재 없음)
- [ ] 프론트 `exploration_node_entity.dart` icon 주석이 이모지(`🌍`) 기준 — 실제 구현은 식별자/국가코드. 주석 정리 필요
- [ ] 행성 목록 응답의 `progress` 객체 ↔ 프론트 `ExplorationProgressEntity` 매핑 확인

### 8.2 시드 로스터 갭 (프론트 시드 vs 백엔드 spec)

> 프론트 `exploration_seed_data.dart`(로컬/게스트용)와 백엔드 spec 예시가 크게 다르다. 회원용 서버 시드를 어느 쪽 기준으로 확정할지 결정 필요.

| 항목 | 프론트 시드 (게스트 로컬) | 백엔드 spec 예시 |
|------|--------------------------|------------------|
| 행성 구성 | 지구·수성·금성·화성·목성·토성·천왕성·해왕성 (8개, **달 없음**) | 지구·달·화성 (예시 3개) |
| 진행 게이트 | **선행조건 없음** (연료만 있으면 해금) | 선행 체인 (지구→달→화성, `prerequisiteId`) |
| 행성 연료 | earth 0 / mercury 3 / venus 5 / mars 10 / jupiter 20 / saturn 30 / uranus 45 / neptune 60 | earth 0 / moon 8 / mars 12 |
| 지구 지역 | 12개 (korea, japan, thailand, china, india, uk, france, canada, usa, brazil, australia, egypt) | 2개 예시 (대한민국, 일본) |
| 지역 ID 규칙 | `korea`, `japan`, `usa` (이름 기반) | `region-kr`, `region-jp` (prefix 기반) |
| 지구 지역 연료 | 0~3 | 4~6 |

> **결정 필요 (백엔드 본인 확인):**
> - [ ] 회원용 서버 시드의 **행성 로스터**를 확정 (8행성 전체인지, 달 포함 여부)
> - [ ] **진행 게이트 모델** 확정 — 선행 행성 클리어 게이트를 쓸지(`prerequisiteId`), 연료만으로 해금할지. 프론트 게스트는 현재 게이트 없음
> - [ ] **지역 ID 네이밍** 통일 (`korea` vs `region-kr`) — 게스트/회원 코드 재사용 위해 한쪽으로 정렬 권장
> - [ ] **연료 밸런스** 확정 후 게스트 시드와 회원 서버 시드 동기화

### 8.3 spec 문서 정합

- [ ] 프론트 레포 `docs/api-specs/05_exploration.md`(구버전: 일본/미국 100연료, 화성 200연료, `prerequisiteId`/`progress` 없음)를 백엔드 신버전(4/6/8/12, prerequisite, progress)으로 갱신

---

## 9. 백엔드 확인 요청 요약

본인(백엔드)에게 확정 요청하는 핵심 항목:

1. **노드 응답 스키마에 `prerequisiteId` 포함** (없으면 null)
2. **행성 목록 응답에 `progress` 객체 포함**
3. **`icon` 값 어휘 고정** (행성 이름 식별자 + 지구 지역 ISO 국가코드) — 6.1 어휘표 합의
4. **에러 `code` 명세 확정** (5번 표) + `INSUFFICIENT_FUEL`에 `requiredFuel/currentFuel` 동봉
5. **해금 응답에 `currentFuel` 포함** (프론트가 fuel 별도 조회 안 하도록)
6. **회원용 서버 시드 확정** — 행성 로스터/진행 게이트/지역 ID 네이밍/연료 밸런스 (8.2)
7. **해금 트랜잭션 원자성 보장** + 오프라인/네트워크 실패 구분 가능한 에러

---

> 게스트는 오직 로컬, 회원은 오직 서버. 이 경계만 지키면 마이그레이션 고민 없이 두 모드를 독립적으로 유지할 수 있다.
