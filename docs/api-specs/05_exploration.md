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
 ├── 지구 (planet) ─ 해금됨
 │   ├── 대한민국 (region) ─ 해금됨
 │   ├── 일본 (region) ─ 잠김 (100연료)
 │   └── 미국 (region) ─ 잠김 (100연료)
 ├── 화성 (planet) ─ 잠김 (200연료)
 │   ├── 올림푸스 (region)
 │   └── 마리너 (region)
 └── ...
```

### 해금 규칙

- **행성 해금**: 연료를 소비하여 행성에 진입 가능 상태로 변경. 지구는 기본 해금.
- **지역 해금**: 행성이 해금된 상태에서 연료를 소비하여 지역 해금 (= 클리어).
- **행성 클리어**: 행성의 모든 하위 지역이 해금되면 자동으로 행성 클리어 처리.
- 연료 차감은 해금 API 내부에서 원자적으로 처리됩니다 (별도 fuel consume 호출 불필요).

### 시드 데이터

행성/지역 마스터 데이터는 서버에서 시드로 관리합니다. ID는 고정 문자열입니다.

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
  "requiredFuel": 0,
  "isUnlocked": true,
  "isCleared": false,
  "sortOrder": 0,
  "description": "모든 여정의 시작점",
  "mapX": 0.5,
  "mapY": 0.3,
  "unlockedAt": "2026-04-01T00:00:00Z"
}
```

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| `id` | String | X | 노드 고유 ID (시드 데이터, 고정 문자열) |
| `name` | String | X | 노드 이름 |
| `nodeType` | String | X | `"planet"` 또는 `"region"` |
| `depth` | Integer | X | 계층 깊이 (planet=2, region=3) |
| `icon` | String | X | 아이콘 식별자 (행성: 이름, 지역: 국가코드) |
| `parentId` | String | O | 상위 노드 ID (행성은 null) |
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
| `planet` | 행성 | 연료 소비 | 모든 하위 region 해금 시 자동 클리어 |
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
    "requiredFuel": 0,
    "isUnlocked": true,
    "isCleared": false,
    "sortOrder": 0,
    "description": "모든 여정의 시작점",
    "mapX": 0.5,
    "mapY": 0.3,
    "unlockedAt": "2026-04-01T00:00:00Z",
    "progress": {
      "clearedChildren": 3,
      "totalChildren": 5,
      "progressRatio": 0.6
    }
  },
  {
    "id": "mars",
    "name": "화성",
    "nodeType": "planet",
    "depth": 2,
    "icon": "mars",
    "parentId": null,
    "requiredFuel": 200,
    "isUnlocked": false,
    "isCleared": false,
    "sortOrder": 1,
    "description": "붉은 행성",
    "mapX": 0.8,
    "mapY": 0.5,
    "unlockedAt": null,
    "progress": {
      "clearedChildren": 0,
      "totalChildren": 3,
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
    "id": "region-kr",
    "name": "대한민국",
    "nodeType": "region",
    "depth": 3,
    "icon": "KR",
    "parentId": "earth",
    "requiredFuel": 100,
    "isUnlocked": true,
    "isCleared": true,
    "sortOrder": 0,
    "description": "한반도의 남쪽",
    "mapX": 0.7,
    "mapY": 0.4,
    "unlockedAt": "2026-04-05T15:30:00Z"
  },
  {
    "id": "region-jp",
    "name": "일본",
    "nodeType": "region",
    "depth": 3,
    "icon": "JP",
    "parentId": "earth",
    "requiredFuel": 100,
    "isUnlocked": false,
    "isCleared": false,
    "sortOrder": 1,
    "description": "해가 뜨는 나라",
    "mapX": 0.8,
    "mapY": 0.3,
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
POST /api/explorations/regions/region-jp/unlock
```

### Response

**200 OK**

```json
{
  "region": {
    "id": "region-jp",
    "name": "일본",
    "isUnlocked": true,
    "isCleared": true,
    "unlockedAt": "2026-04-16T11:00:00Z"
  },
  "fuelConsumed": 100,
  "currentFuel": 250,
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
POST /api/explorations/planets/mars/unlock
```

### Response

**200 OK**

```json
{
  "planet": {
    "id": "mars",
    "name": "화성",
    "isUnlocked": true,
    "isCleared": false,
    "unlockedAt": "2026-04-16T11:30:00Z"
  },
  "fuelConsumed": 200,
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
| 404 | `PLANET_NOT_FOUND` | planetId에 해당하는 행성 없음 |

### 서버 처리 로직

```
BEGIN TRANSACTION;
  1. planetId로 행성 마스터 데이터 조회
  2. 이미 해금된 행성인지 확인
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
| `id` | VARCHAR(50) (PK) | 노드 ID (earth, mars, region-kr 등) |
| `name` | VARCHAR(50) | 노드 이름 |
| `node_type` | VARCHAR(10) | planet / region |
| `depth` | INTEGER | 계층 깊이 |
| `icon` | VARCHAR(20) | 아이콘 식별자 |
| `parent_id` | VARCHAR(50) (FK → self) | 상위 노드 ID |
| `required_fuel` | INTEGER | 해금 필요 연료 |
| `sort_order` | INTEGER | 표시 순서 |
| `description` | VARCHAR(200) | 설명 |
| `map_x` | DOUBLE | 맵 가로 위치 |
| `map_y` | DOUBLE | 맵 세로 위치 |

### user_exploration_progress (유저별 진행 상태)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT (PK) | |
| `user_id` | BIGINT (FK → users) | 유저 ID |
| `node_id` | VARCHAR(50) (FK → exploration_nodes) | 노드 ID |
| `is_unlocked` | BOOLEAN | 해금 여부 |
| `is_cleared` | BOOLEAN | 클리어 여부 |
| `unlocked_at` | TIMESTAMP | 해금 시각 |

UNIQUE 제약: (`user_id`, `node_id`)
