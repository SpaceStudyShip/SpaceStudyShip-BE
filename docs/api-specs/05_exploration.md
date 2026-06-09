# 05. Exploration (탐험)

> Base Path: `/api/explorations`
> 엔드포인트: 4개
> 동기화: **Tier 2 (Server-Validated)**
> 공통 규칙: [00_common.md](./00_common.md) 참조

---

## 탐험 시스템 개요

우주 탐험은 트리 구조의 노드(행성 → 지역)를 연료로 해금하는 시스템입니다.

```
태양계 (고정)
 ├── 지구 (planet, fuel=0) ─ 기본 해금
 │   ├── 대한민국 (region, fuel=0) ─ 기본 해금
 │   ├── 일본 (region, fuel=1)
 │   └── ... (총 12개 지역)
 ├── 수성 (planet, fuel=3) ─ 지구 클리어 후 해금 가능
 ├── 금성 (planet, fuel=5) ─ 수성 클리어 후 해금 가능
 ├── 화성 (planet, fuel=10) ─ 금성 클리어 후 해금 가능
 ├── 목성 (planet, fuel=20) ─ 화성 클리어 후 해금 가능
 ├── 토성 (planet, fuel=30) ─ 목성 클리어 후 해금 가능
 ├── 천왕성 (planet, fuel=45) ─ 토성 클리어 후 해금 가능
 └── 해왕성 (planet, fuel=60) ─ 천왕성 클리어 후 해금 가능
```

### 해금 규칙

- **행성 해금**: 연료를 소비하여 행성에 진입 가능 상태로 변경. 지구는 기본 해금 (`requiredFuel=0`).
- **행성 진행 게이트**: 행성은 선행 행성(`prerequisiteId`)을 클리어해야 해금. 지구는 선행 없음 (체인: 지구→수성→금성→화성→목성→토성→천왕성→해왕성).
- **지역 해금**: 행성이 해금된 상태에서 연료를 소비하여 지역 해금 (= 클리어).
- **행성 클리어**: 행성의 모든 하위 지역이 해금되면 자동으로 행성 클리어 처리.
- 연료 차감은 해금 API 내부에서 원자적으로 처리됩니다 (별도 fuel consume 호출 불필요).

### 시드 데이터

행성/지역 마스터 데이터는 서버에서 시드로 관리합니다. ID는 고정 문자열입니다.
- **행성 ID**: `earth`, `mercury`, `venus`, `mars`, `jupiter`, `saturn`, `uranus`, `neptune` (총 8개)
- **지역 ID**: 이름 기반 문자열 (예: `korea`, `japan`, `mars_olympus`)
- **icon 값**: 지구 지역은 국가 코드 (예: `KR`, `JP`), 그 외 행성/행성 지역은 행성 이름 (예: `mars`, `jupiter`)

---

## 엔드포인트 요약

| # | Method | Path | 설명 |
|---|--------|------|------|
| 1 | GET | `/api/explorations/planets` | 행성 목록 조회 |
| 2 | GET | `/api/explorations/planets/{planetId}/regions` | 지역 목록 조회 |
| 3 | POST | `/api/explorations/regions/{regionId}/unlock` | 지역 해금 |
| 4 | POST | `/api/explorations/planets/{planetId}/unlock` | 행성 해금 |

---

## 탐험 노드 객체 구조

행성과 지역은 동일한 노드 구조를 공유합니다.

```json
{
  "id": "earth",
  "name": "지구",
  "nodeType": "planet",
  "depth": 2,
  "icon": "earth",
  "parentId": null,
  "prerequisiteId": null,
  "requiredFuel": 0,
  "isUnlocked": true,
  "isCleared": false,
  "sortOrder": 0,
  "description": "우리의 출발지, 고향 행성",
  "mapX": 0.5,
  "mapY": 0.08,
  "unlockedAt": "2026-04-01T00:00:00Z"
}
```

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| `id` | String | X | 노드 고유 ID (시드 데이터, 고정 문자열) |
| `name` | String | X | 노드 이름 |
| `nodeType` | String | X | `"planet"` 또는 `"region"` |
| `depth` | Integer | X | 계층 깊이 (planet=2, region=3) |
| `icon` | String | X | 아이콘 식별자 (지구 지역: 국가코드, 그 외: 행성이름) |
| `parentId` | String | O | 상위 노드 ID (행성은 null) |
| `prerequisiteId` | String | O | 선행 행성 ID (행성만, 이 행성을 해금하려면 선행 행성을 클리어해야 함). region은 null |
| `requiredFuel` | Integer | X | 해금에 필요한 연료량 (0이면 기본 해금) |
| `isUnlocked` | Boolean | X | 해금 여부 |
| `isCleared` | Boolean | X | 클리어 여부 (지역: 해금=클리어, 행성: 모든 지역 해금 시 클리어) |
| `sortOrder` | Integer | X | 표시 순서 |
| `description` | String | X | 노드 설명 |
| `mapX` | Double | X | 맵 가로 위치 (0.0~1.0) |
| `mapY` | Double | X | 맵 세로 위치 (0.0~1.0) |
| `unlockedAt` | String | O | 해금 시각 (null = 미해금) |

### nodeType 값

| 값 | 설명 | 해금 조건 | 클리어 조건 |
|----|------|----------|-----------|
| `planet` | 행성 | 연료 소비 + 선행 행성 클리어 | 모든 하위 region 해금 시 자동 클리어 |
| `region` | 지역 | 연료 소비 (상위 행성 해금 필수) | 해금 = 클리어 |

---

## 1. 행성 목록 조회

`GET /api/explorations/planets`

전체 행성 목록과 사용자의 해금/클리어 상태, 진행도를 함께 반환합니다.

### 인증: 필요

### Query Parameters: 없음

### Response

**200 OK**

```json
[
  {
    "id": "earth",
    "name": "지구",
    "nodeType": "planet",
    "depth": 2,
    "icon": "earth",
    "parentId": null,
    "prerequisiteId": null,
    "requiredFuel": 0,
    "isUnlocked": true,
    "isCleared": false,
    "sortOrder": 0,
    "description": "우리의 출발지, 고향 행성",
    "mapX": 0.5,
    "mapY": 0.08,
    "unlockedAt": "2026-04-01T00:00:00Z",
    "progress": {
      "clearedChildren": 3,
      "totalChildren": 12,
      "progressRatio": 0.25
    }
  },
  {
    "id": "mercury",
    "name": "수성",
    "nodeType": "planet",
    "depth": 2,
    "icon": "mercury",
    "parentId": null,
    "prerequisiteId": "earth",
    "requiredFuel": 3,
    "isUnlocked": false,
    "isCleared": false,
    "sortOrder": 1,
    "description": "태양에 가장 가까운 작은 행성",
    "mapX": 0.15,
    "mapY": 0.20,
    "unlockedAt": null,
    "progress": {
      "clearedChildren": 0,
      "totalChildren": 2,
      "progressRatio": 0.0
    }
  }
]
```

### progress 객체

| 필드 | 타입 | 설명 |
|------|------|------|
| `clearedChildren` | Integer | 해금(클리어)된 하위 지역 수 |
| `totalChildren` | Integer | 전체 하위 지역 수 |
| `progressRatio` | Double | 진행률 (0.0~1.0) |

정렬: `sortOrder` 오름차순

---

## 2. 행성 하위 지역 목록 조회

`GET /api/explorations/planets/{planetId}/regions`

특정 행성의 모든 하위 지역과 사용자의 해금 상태를 반환합니다.

### 인증: 필요

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `planetId` | String | 행성 ID |

```
GET /api/explorations/planets/earth/regions
```

### Response

**200 OK**

```json
[
  {
    "id": "korea",
    "name": "대한민국",
    "nodeType": "region",
    "depth": 3,
    "icon": "KR",
    "parentId": "earth",
    "requiredFuel": 0,
    "isUnlocked": true,
    "isCleared": true,
    "sortOrder": 0,
    "description": "한반도 남쪽, K-컬쳐의 중심",
    "mapX": 0.0,
    "mapY": 0.0,
    "unlockedAt": "2026-04-05T15:30:00Z"
  },
  {
    "id": "japan",
    "name": "일본",
    "nodeType": "region",
    "depth": 3,
    "icon": "JP",
    "parentId": "earth",
    "requiredFuel": 1,
    "isUnlocked": false,
    "isCleared": false,
    "sortOrder": 1,
    "description": "벚꽃과 기술의 나라",
    "mapX": 0.0,
    "mapY": 0.0,
    "unlockedAt": null
  }
]
```

### Error

| Status | code | 상황 |
|--------|------|------|
| 404 | `PLANET_NOT_FOUND` | planetId에 해당하는 행성 없음 |

정렬: `sortOrder` 오름차순

---

## 3. 지역 해금

`POST /api/explorations/regions/{regionId}/unlock`

연료를 소비하여 지역을 해금합니다.
서버에서 연료 잔량 확인 + 차감 + 해금 상태 변경을 원자적으로 처리합니다.
별도의 fuel consume API 호출은 불필요합니다.

### 인증: 필요

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `regionId` | String | 해금할 지역 ID |

### Request Body: 없음

```
POST /api/explorations/regions/japan/unlock
```

### Response

**200 OK**

```json
{
  "region": {
    "id": "japan",
    "name": "일본",
    "isUnlocked": true,
    "isCleared": true,
    "unlockedAt": "2026-04-16T11:00:00Z"
  },
  "fuelConsumed": 1,
  "currentFuel": 25,
  "planetCleared": false
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `region` | Object | 해금된 지역 정보 |
| `fuelConsumed` | Integer | 소비된 연료량 |
| `currentFuel` | Integer | 소비 후 남은 연료 잔량 |
| `planetCleared` | Boolean | 이 해금으로 상위 행성이 클리어되었는지 여부 |

### Error

| Status | code | 상황 |
|--------|------|------|
| 400 | `INSUFFICIENT_FUEL` | 연료 잔량 부족 (`currentFuel < requiredFuel`) |
| 400 | `ALREADY_UNLOCKED` | 이미 해금된 지역 |
| 400 | `PLANET_LOCKED` | 상위 행성이 아직 해금되지 않음 |
| 404 | `REGION_NOT_FOUND` | regionId에 해당하는 지역 없음 |

**INSUFFICIENT_FUEL 응답 본문 예시:**

```json
{ "code": "INSUFFICIENT_FUEL", "message": "연료가 부족합니다.", "requiredFuel": 10, "currentFuel": 4 }
```

### 서버 처리 로직

```
BEGIN TRANSACTION;
  1. regionId로 지역 마스터 데이터 조회
  2. 상위 행성(parentId)이 해금 상태인지 확인
  3. 이미 해금된 지역인지 확인
  4. 유저 연료 잔량 >= requiredFuel 확인
  5. 연료 차감: user_fuel.current_fuel -= requiredFuel
  6. 연료 거래 내역 생성 (type: consume, reason: EXPLORATION_UNLOCK, referenceId: regionId)
  7. 지역 해금 상태 저장 (user_exploration_progress)
  8. 상위 행성의 모든 지역이 해금되었는지 확인
     → 모두 해금: 행성 클리어 상태 업데이트, planetCleared = true
COMMIT;
```

---

## 4. 행성 해금

`POST /api/explorations/planets/{planetId}/unlock`

연료를 소비하여 행성을 해금합니다 (행성 진입 가능 상태로 변경).
서버에서 연료 잔량 확인 + 차감 + 해금을 원자적으로 처리합니다.

### 인증: 필요

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `planetId` | String | 해금할 행성 ID |

### Request Body: 없음

```
POST /api/explorations/planets/mercury/unlock
```

### Response

**200 OK**

```json
{
  "planet": {
    "id": "mercury",
    "name": "수성",
    "isUnlocked": true,
    "isCleared": false,
    "unlockedAt": "2026-04-16T11:30:00Z"
  },
  "fuelConsumed": 3,
  "currentFuel": 50
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `planet` | Object | 해금된 행성 정보 |
| `fuelConsumed` | Integer | 소비된 연료량 |
| `currentFuel` | Integer | 소비 후 남은 연료 잔량 |

### Error

| Status | code | 상황 |
|--------|------|------|
| 400 | `INSUFFICIENT_FUEL` | 연료 잔량 부족 |
| 400 | `ALREADY_UNLOCKED` | 이미 해금된 행성 |
| 400 | `PREREQUISITE_NOT_CLEARED` | 선행 행성이 아직 클리어되지 않음 |
| 404 | `PLANET_NOT_FOUND` | planetId에 해당하는 행성 없음 |

**INSUFFICIENT_FUEL 응답 본문 예시:**

```json
{ "code": "INSUFFICIENT_FUEL", "message": "연료가 부족합니다.", "requiredFuel": 10, "currentFuel": 4 }
```

### 서버 처리 로직

```
BEGIN TRANSACTION;
  1. planetId로 행성 마스터 데이터 조회
  2. 이미 해금된 행성인지 확인
  2-1. prerequisiteId가 있으면 선행 행성이 클리어(모든 하위 지역 해금)되었는지 확인 → 아니면 PREREQUISITE_NOT_CLEARED
  3. 유저 연료 잔량 >= requiredFuel 확인
  4. 연료 차감
  5. 연료 거래 내역 생성 (type: consume, reason: EXPLORATION_UNLOCK, referenceId: planetId)
  6. 행성 해금 상태 저장
COMMIT;
```

---

## DB 테이블 참고

### exploration_nodes (시드 데이터, 읽기 전용)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | VARCHAR(50) (PK) | 노드 ID (이름 기반 고정 문자열: `earth`, `korea`, `mars_olympus` 등) |
| `name` | VARCHAR(50) | 노드 이름 |
| `node_type` | VARCHAR(10) | planet / region |
| `depth` | INTEGER | 계층 깊이 |
| `icon` | VARCHAR(30) | 아이콘 식별자 (지구 지역: 국가코드 예: `KR`, 그 외: 행성이름 예: `mars`) |
| `parent_id` | VARCHAR(50) (FK → self) | 상위 노드 ID |
| `prerequisite_node_id` | VARCHAR(50) (FK → self) | 선행 행성 ID (행성만, 지역은 NULL) |
| `required_fuel` | INTEGER | 해금 필요 연료 |
| `sort_order` | INTEGER | 표시 순서 |
| `description` | VARCHAR(200) | 설명 |
| `map_x` | DOUBLE | 맵 가로 위치 |
| `map_y` | DOUBLE | 맵 세로 위치 |

**행성 시드 (8개):**

| id | name | required_fuel | prerequisite_node_id | sort_order |
|----|------|:---:|---|:---:|
| `earth` | 지구 | 0 | NULL | 0 |
| `mercury` | 수성 | 3 | `earth` | 1 |
| `venus` | 금성 | 5 | `mercury` | 2 |
| `mars` | 화성 | 10 | `venus` | 3 |
| `jupiter` | 목성 | 20 | `mars` | 4 |
| `saturn` | 토성 | 30 | `jupiter` | 5 |
| `uranus` | 천왕성 | 45 | `saturn` | 6 |
| `neptune` | 해왕성 | 60 | `uranus` | 7 |

**지역 시드 (30개, required_fuel 범위: 0~20):**

지역 ID는 이름 기반 문자열 (예: `korea`, `japan`, `mars_olympus`). 지구 12개, 그 외 행성 각 2~3개.

### user_exploration_progress (유저별 진행 상태)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT (PK) | |
| `user_id` | BIGINT (FK → members) | 유저 ID |
| `node_id` | VARCHAR(50) (FK → exploration_nodes) | 노드 ID |
| `is_unlocked` | BOOLEAN | 해금 여부 |
| `is_cleared` | BOOLEAN | 클리어 여부 |
| `unlocked_at` | TIMESTAMP | 해금 시각 |

UNIQUE 제약: (`user_id`, `node_id`)
