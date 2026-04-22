# 02. Todo (할 일 + 카테고리)

> Base Path: `/api/todos`, `/api/todo-categories`
> 엔드포인트: 8개
> 동기화: **Tier 1 (Optimistic Updates)**
> 공통 규칙: [00_common.md](./00_common.md) 참조

---

## 동기화 전략

Todo는 **Optimistic Updates** 방식입니다.

```
1. 클라이언트가 로컬 DB에 먼저 저장 (임시 UUID 생성)
2. UI 즉시 반영
3. 백그라운드에서 서버 API 호출
4. 성공: 서버 응답으로 로컬 데이터 갱신
5. 실패: 재시도 큐에 추가 (최대 3회)
```

---

## 엔드포인트 요약

| # | Method | Path | 설명 |
|---|--------|------|------|
| 1 | GET | `/api/todos` | Todo 목록 조회 |
| 2 | POST | `/api/todos` | Todo 생성 |
| 3 | PATCH | `/api/todos/{todoId}` | Todo 수정 |
| 4 | DELETE | `/api/todos/{todoId}` | Todo 삭제 |
| 5 | GET | `/api/todo-categories` | 카테고리 목록 조회 |
| 6 | POST | `/api/todo-categories` | 카테고리 생성 |
| 7 | PATCH | `/api/todo-categories/{categoryId}` | 카테고리 수정 |
| 8 | DELETE | `/api/todo-categories/{categoryId}` | 카테고리 삭제 |

---

## Todo 객체 구조

모든 Todo 응답에서 사용되는 공통 구조입니다.

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "수학 문제 풀기",
  "scheduledDates": ["2026-04-16", "2026-04-17"],
  "completedDates": ["2026-04-16"],
  "categoryIds": ["cat-uuid-1"],
  "estimatedMinutes": 60,
  "actualMinutes": 45,
  "createdAt": "2026-04-15T10:00:00Z",
  "updatedAt": "2026-04-16T09:30:00Z"
}
```

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| `id` | String (UUID) | X | Todo 고유 ID |
| `title` | String | X | 제목 (1~100자) |
| `scheduledDates` | String[] | X | 예정 날짜 목록 (`YYYY-MM-DD`). 빈 배열 가능 |
| `completedDates` | String[] | X | 완료 날짜 목록 (`YYYY-MM-DD`). 빈 배열 가능 |
| `categoryIds` | String[] | X | 소속 카테고리 ID 목록. 빈 배열 가능 |
| `estimatedMinutes` | Integer | O | 예상 소요 시간 (분) |
| `actualMinutes` | Integer | O | 실제 소요 시간 (분, 타이머 연동) |
| `createdAt` | String | X | 생성 시각 (ISO 8601 UTC) |
| `updatedAt` | String | X | 마지막 수정 시각 (ISO 8601 UTC) |

### 완료 판정 로직

- 특정 날짜에 완료: `completedDates`에 해당 날짜가 포함되어 있으면 완료
- 전체 완료: `scheduledDates`의 모든 날짜가 `completedDates`에 포함
- `scheduledDates`가 비어있고 `completedDates`에 값이 있으면 완료로 간주

---

## 1. Todo 목록 조회

`GET /api/todos`

### 인증: 필요

### Query Parameters

| 파라미터 | 타입 | 필수 | 설명 | 예시 |
|---------|------|------|------|------|
| `date` | String | X | 특정 날짜에 예정된 Todo만 필터 | `2026-04-16` |
| `categoryId` | String | X | 특정 카테고리에 속한 Todo만 필터 | `cat-uuid-1` |

```
GET /api/todos
GET /api/todos?date=2026-04-16
GET /api/todos?categoryId=cat-uuid-1
GET /api/todos?date=2026-04-16&categoryId=cat-uuid-1
```

### Response

**200 OK**

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "수학 문제 풀기",
    "scheduledDates": ["2026-04-16", "2026-04-17"],
    "completedDates": ["2026-04-16"],
    "categoryIds": ["cat-uuid-1"],
    "estimatedMinutes": 60,
    "actualMinutes": 45,
    "createdAt": "2026-04-15T10:00:00Z",
    "updatedAt": "2026-04-16T09:30:00Z"
  },
  {
    "id": "660f9511-f3ac-52e5-b827-557766551111",
    "title": "영어 단어 외우기",
    "scheduledDates": ["2026-04-16"],
    "completedDates": [],
    "categoryIds": [],
    "estimatedMinutes": null,
    "actualMinutes": null,
    "createdAt": "2026-04-16T08:00:00Z",
    "updatedAt": "2026-04-16T08:00:00Z"
  }
]
```

### 필터 로직

- `date` 필터: `scheduledDates` 배열에 해당 날짜가 포함된 Todo 반환
- `categoryId` 필터: `categoryIds` 배열에 해당 ID가 포함된 Todo 반환
- 필터 없음: 해당 유저의 모든 Todo 반환
- 정렬: `createdAt` 내림차순 (최신순)

---

## 2. Todo 생성

`POST /api/todos`

### 인증: 필요

### Request Body

| 필드 | 타입 | 필수 | 제약조건 | 설명 |
|------|------|------|---------|------|
| `id` | String | X | UUID v4 | 클라이언트에서 생성한 ID (없으면 서버 생성) |
| `title` | String | O | 1~100자 | Todo 제목 |
| `categoryIds` | String[] | X | 유효한 카테고리 ID | 소속 카테고리 (기본: `[]`) |
| `estimatedMinutes` | Integer | X | 1 이상 | 예상 소요 시간 (분) |
| `scheduledDates` | String[] | X | `YYYY-MM-DD` 형식 | 예정 날짜 목록 (기본: `[]`) |

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "수학 문제 풀기",
  "categoryIds": ["cat-uuid-1"],
  "estimatedMinutes": 60,
  "scheduledDates": ["2026-04-16", "2026-04-17"]
}
```

### Response

**201 Created**

Todo 객체 전체 반환 (위 공통 구조 참조)

### Error

| Status | code | 상황 |
|--------|------|------|
| 400 | `INVALID_TITLE` | 제목이 비어있거나 100자 초과 |
| 400 | `INVALID_DATE_FORMAT` | scheduledDates 형식 오류 |
| 404 | `CATEGORY_NOT_FOUND` | categoryIds에 존재하지 않는 카테고리 |
| 409 | `TODO_ALREADY_EXISTS` | 동일 ID의 Todo가 이미 존재 |

---

## 3. Todo 수정

`PATCH /api/todos/{todoId}`

변경할 필드만 전송합니다. 전송하지 않은 필드는 기존 값을 유지합니다.

### 인증: 필요

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `todoId` | String (UUID) | 수정할 Todo ID |

### Request Body (변경할 필드만 전송)

| 필드 | 타입 | 설명 |
|------|------|------|
| `title` | String | 제목 (1~100자) |
| `scheduledDates` | String[] | 예정 날짜 목록 (`YYYY-MM-DD`) |
| `completedDates` | String[] | 완료 날짜 목록 (`YYYY-MM-DD`) |
| `categoryIds` | String[] | 카테고리 ID 목록 |
| `estimatedMinutes` | Integer | 예상 소요 시간 (분) |
| `actualMinutes` | Integer | 실제 소요 시간 (분) |

**예시: 특정 날짜 완료 처리**

```json
{
  "completedDates": ["2026-04-16", "2026-04-17"]
}
```

**예시: 제목 + 카테고리 변경**

```json
{
  "title": "수학 심화 문제",
  "categoryIds": ["cat-uuid-2"]
}
```

### Response

**200 OK** - 수정된 Todo 객체 전체 반환

### Error

| Status | code | 상황 |
|--------|------|------|
| 404 | `TODO_NOT_FOUND` | todoId에 해당하는 Todo 없음 |
| 403 | `FORBIDDEN` | 다른 유저의 Todo 수정 시도 |

---

## 4. Todo 삭제

`DELETE /api/todos/{todoId}`

### 인증: 필요

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `todoId` | String (UUID) | 삭제할 Todo ID |

### Response

**204 No Content**

### Error

| Status | code | 상황 |
|--------|------|------|
| 404 | `TODO_NOT_FOUND` | todoId에 해당하는 Todo 없음 |

### 서버 처리

- 해당 Todo와 연결된 타이머 세션의 `todoId`는 유지 (히스토리 보존)
- `todoTitle` 스냅샷이 타이머 세션에 저장되어 있으므로 삭제해도 세션 표시에 영향 없음

---

## 카테고리 객체 구조

```json
{
  "id": "cat-uuid-1",
  "name": "수학",
  "iconId": "math_icon",
  "positionX": 0.3,
  "positionY": 0.5,
  "createdAt": "2026-04-01T00:00:00Z",
  "updatedAt": null
}
```

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| `id` | String (UUID) | X | 카테고리 고유 ID |
| `name` | String | X | 카테고리 이름 (1~20자) |
| `iconId` | String | O | 아이콘 식별자 |
| `positionX` | Double | O | 카테고리 맵 가로 위치 (0.0~1.0) |
| `positionY` | Double | O | 카테고리 맵 세로 위치 (0.0~1.0) |
| `createdAt` | String | X | 생성 시각 |
| `updatedAt` | String | O | 마지막 수정 시각 |

---

## 5. 카테고리 목록 조회

`GET /api/todo-categories`

### 인증: 필요

### Response

**200 OK**

```json
[
  {
    "id": "cat-uuid-1",
    "name": "수학",
    "iconId": "math_icon",
    "positionX": 0.3,
    "positionY": 0.5,
    "createdAt": "2026-04-01T00:00:00Z",
    "updatedAt": null
  },
  {
    "id": "cat-uuid-2",
    "name": "영어",
    "iconId": "english_icon",
    "positionX": 0.7,
    "positionY": 0.3,
    "createdAt": "2026-04-02T00:00:00Z",
    "updatedAt": "2026-04-10T15:00:00Z"
  }
]
```

정렬: `createdAt` 오름차순

---

## 6. 카테고리 생성

`POST /api/todo-categories`

### 인증: 필요

### Request Body

| 필드 | 타입 | 필수 | 제약조건 | 설명 |
|------|------|------|---------|------|
| `id` | String | X | UUID v4 | 클라이언트에서 생성한 ID (없으면 서버 생성) |
| `name` | String | O | 1~20자 | 카테고리 이름 |
| `iconId` | String | X | | 아이콘 식별자 |
| `positionX` | Double | X | 0.0~1.0 | 맵 가로 위치 |
| `positionY` | Double | X | 0.0~1.0 | 맵 세로 위치 |

```json
{
  "id": "cat-uuid-3",
  "name": "과학",
  "iconId": "science_icon",
  "positionX": 0.5,
  "positionY": 0.8
}
```

### Response

**201 Created** - 생성된 카테고리 객체

### Error

| Status | code | 상황 |
|--------|------|------|
| 400 | `INVALID_CATEGORY_NAME` | 이름이 비어있거나 20자 초과 |
| 409 | `CATEGORY_ALREADY_EXISTS` | 동일 ID의 카테고리가 이미 존재 |

---

## 7. 카테고리 수정

`PATCH /api/todo-categories/{categoryId}`

변경할 필드만 전송합니다.

### 인증: 필요

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `categoryId` | String (UUID) | 수정할 카테고리 ID |

### Request Body (변경할 필드만 전송)

| 필드 | 타입 | 설명 |
|------|------|------|
| `name` | String | 카테고리 이름 (1~20자) |
| `iconId` | String | 아이콘 식별자 |
| `positionX` | Double | 맵 가로 위치 (0.0~1.0) |
| `positionY` | Double | 맵 세로 위치 (0.0~1.0) |

```json
{
  "name": "수학(심화)",
  "positionX": 0.4
}
```

### Response

**200 OK** - 수정된 카테고리 객체

### Error

| Status | code | 상황 |
|--------|------|------|
| 404 | `CATEGORY_NOT_FOUND` | 해당 카테고리 없음 |

---

## 8. 카테고리 삭제

`DELETE /api/todo-categories/{categoryId}`

### 인증: 필요

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `categoryId` | String (UUID) | 삭제할 카테고리 ID |

### Response

**204 No Content**

### 서버 처리

카테고리 삭제 시 해당 카테고리에 속한 모든 Todo의 `categoryIds`에서 해당 ID를 자동으로 제거합니다. Todo 자체는 삭제되지 않습니다.

```sql
-- 예시: categoryIds에서 삭제된 카테고리 ID 제거
UPDATE todos
SET category_ids = array_remove(category_ids, :deletedCategoryId)
WHERE user_id = :userId AND :deletedCategoryId = ANY(category_ids);
```

---

## DB 테이블 참고

### todos

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | VARCHAR(36) (PK) | UUID |
| `user_id` | BIGINT (FK → users) | 소유자 |
| `title` | VARCHAR(100) | 제목 |
| `scheduled_dates` | JSON / TEXT[] | 예정 날짜 배열 |
| `completed_dates` | JSON / TEXT[] | 완료 날짜 배열 |
| `category_ids` | JSON / TEXT[] | 카테고리 ID 배열 |
| `estimated_minutes` | INTEGER | 예상 소요 시간 |
| `actual_minutes` | INTEGER | 실제 소요 시간 |
| `created_at` | TIMESTAMP | 생성일 |
| `updated_at` | TIMESTAMP | 수정일 |

### todo_categories

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | VARCHAR(36) (PK) | UUID |
| `user_id` | BIGINT (FK → users) | 소유자 |
| `name` | VARCHAR(20) | 카테고리 이름 |
| `icon_id` | VARCHAR(50) | 아이콘 식별자 |
| `position_x` | DOUBLE | 맵 가로 위치 |
| `position_y` | DOUBLE | 맵 세로 위치 |
| `created_at` | TIMESTAMP | 생성일 |
| `updated_at` | TIMESTAMP | 수정일 |
