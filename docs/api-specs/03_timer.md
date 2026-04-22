# 03. Timer (공부 타이머)

> Base Path: `/api/timer-sessions`
> 엔드포인트: 3개
> 동기화: **Tier 2 (Server-Validated)**
> 공통 규칙: [00_common.md](./00_common.md) 참조

---

## 동기화 전략

Timer 세션은 **Server-Validated** 방식입니다.

```
1. 클라이언트에서 타이머 시작/일시정지/재개 (로컬에서 관리)
2. 타이머 종료 시 서버에 세션 데이터 전송
3. 서버에서 시간 유효성 검증 + 연료 계산
4. 서버 응답의 확정값으로 로컬 업데이트
```

핵심: 연료 충전량은 클라이언트가 보낸 `durationMinutes`가 아닌, **서버에서 재계산한 값**을 사용합니다 (조작 방지).

---

## 엔드포인트 요약

| # | Method | Path | 설명 |
|---|--------|------|------|
| 1 | GET | `/api/timer-sessions` | 세션 목록 조회 |
| 2 | POST | `/api/timer-sessions` | 세션 기록 저장 |
| 3 | GET | `/api/timer-sessions/today-stats` | 오늘 공부 통계 |

---

## 세션 객체 구조

```json
{
  "id": "session-uuid-1234",
  "todoId": "todo-uuid-5678",
  "todoTitle": "수학 문제 풀기",
  "startedAt": "2026-04-16T09:00:00Z",
  "endedAt": "2026-04-16T10:30:00Z",
  "durationMinutes": 90
}
```

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| `id` | String (UUID) | X | 세션 고유 ID (서버 생성) |
| `todoId` | String (UUID) | O | 연결된 Todo ID (Todo 없이 타이머만 사용 가능) |
| `todoTitle` | String | O | Todo 제목 스냅샷 (Todo 삭제 후에도 표시용) |
| `startedAt` | String | X | 타이머 시작 시각 (ISO 8601 UTC) |
| `endedAt` | String | X | 타이머 종료 시각 (ISO 8601 UTC) |
| `durationMinutes` | Integer | X | 실제 공부 시간 (분, 일시정지 제외) |

---

## 1. 세션 목록 조회

`GET /api/timer-sessions`

### 인증: 필요

### Query Parameters

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| `startDate` | String | X | - | 시작일 이후 세션 필터 (`YYYY-MM-DD`) |
| `endDate` | String | X | - | 종료일 이전 세션 필터 (`YYYY-MM-DD`) |
| `todoId` | String | X | - | 특정 Todo에 연결된 세션만 |
| `page` | Integer | X | 0 | 페이지 번호 (0-indexed) |
| `size` | Integer | X | 20 | 페이지당 항목 수 (최대 100) |

```
GET /api/timer-sessions?startDate=2026-04-01&endDate=2026-04-16&page=0&size=20
GET /api/timer-sessions?todoId=todo-uuid-5678
```

### Response

**200 OK**

```json
{
  "content": [
    {
      "id": "session-uuid-1",
      "todoId": "todo-uuid-5678",
      "todoTitle": "수학 문제 풀기",
      "startedAt": "2026-04-16T09:00:00Z",
      "endedAt": "2026-04-16T10:30:00Z",
      "durationMinutes": 90
    },
    {
      "id": "session-uuid-2",
      "todoId": null,
      "todoTitle": null,
      "startedAt": "2026-04-16T13:00:00Z",
      "endedAt": "2026-04-16T14:00:00Z",
      "durationMinutes": 60
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 45,
  "totalPages": 3
}
```

정렬: `startedAt` 내림차순 (최신순)

---

## 2. 세션 기록 저장

`POST /api/timer-sessions`

타이머 종료 시 세션 기록을 저장합니다.
서버에서 시간 유효성을 검증하고, 검증 통과 시 연료를 자동 충전합니다.

### 인증: 필요

### Request Body

| 필드 | 타입 | 필수 | 제약조건 | 설명 |
|------|------|------|---------|------|
| `todoId` | String | X | 유효한 Todo UUID | 연결된 Todo ID |
| `todoTitle` | String | X | 1~100자 | Todo 제목 스냅샷 |
| `startedAt` | String | O | ISO 8601 UTC | 타이머 시작 시각 |
| `endedAt` | String | O | ISO 8601 UTC, startedAt 이후 | 타이머 종료 시각 |
| `durationMinutes` | Integer | O | 1 이상 | 실제 공부 시간 (분, 일시정지 제외) |

```json
{
  "todoId": "todo-uuid-5678",
  "todoTitle": "수학 문제 풀기",
  "startedAt": "2026-04-16T09:00:00Z",
  "endedAt": "2026-04-16T10:30:00Z",
  "durationMinutes": 90
}
```

### Response

**201 Created**

```json
{
  "session": {
    "id": "server-generated-uuid",
    "todoId": "todo-uuid-5678",
    "todoTitle": "수학 문제 풀기",
    "startedAt": "2026-04-16T09:00:00Z",
    "endedAt": "2026-04-16T10:30:00Z",
    "durationMinutes": 90
  },
  "fuelCharged": 90
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `session` | Object | 저장된 세션 (서버 생성 ID 포함) |
| `fuelCharged` | Integer | 서버에서 검증 후 실제 충전된 연료량 |

### Error

| Status | code | 상황 |
|--------|------|------|
| 400 | `INVALID_SESSION_TIME` | `startedAt` > `endedAt` |
| 400 | `INVALID_DURATION` | `durationMinutes`가 `endedAt - startedAt`보다 큼 |
| 400 | `SESSION_TOO_SHORT` | `durationMinutes`가 1분 미만 |
| 400 | `SESSION_TOO_LONG` | `durationMinutes`가 24시간(1440분) 초과 |
| 400 | `FUTURE_SESSION` | `startedAt`이 현재 시각보다 미래 |

### 서버 검증 로직

```
1. startedAt < endedAt 확인
2. durationMinutes <= (endedAt - startedAt) 확인 (일시정지 포함이므로 작거나 같아야 함)
3. durationMinutes >= 1 확인
4. startedAt이 미래가 아닌지 확인
5. 검증 통과 시:
   - 세션 DB 저장
   - 연료 충전: fuelCharged = durationMinutes (1분 = 1연료)
   - Fuel 거래 내역 생성 (type: charge, reason: STUDY_SESSION, referenceId: sessionId)
6. Todo에 actualMinutes 누적 업데이트 (todoId가 있는 경우)
```

### 연료 충전 규칙

- 기본: **1분 공부 = 1 연료**
- 서버에서 `durationMinutes`를 재검증하여 충전량 결정
- 클라이언트가 보낸 값과 서버 계산값이 다를 수 있음 (조작 방지)

---

## 3. 오늘 공부 통계

`GET /api/timer-sessions/today-stats`

오늘 날짜의 공부 요약 통계를 조회합니다.

### 인증: 필요

### Query Parameters: 없음

### Response

**200 OK**

```json
{
  "totalMinutes": 180,
  "sessionCount": 3,
  "streak": 7
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `totalMinutes` | Integer | 오늘 총 공부 시간 (분) |
| `sessionCount` | Integer | 오늘 완료한 세션 수 |
| `streak` | Integer | 연속 공부 일수 (오늘 포함) |

### 연속 일수 (Streak) 계산 로직

```
1. 공부한 날짜 집합 추출 (startedAt 기준, 날짜만)
2. 중복 제거 후 최신순 정렬
3. 오늘 또는 어제 공부했는지 확인
   - 둘 다 아님: streak = 0
4. 최신 날짜부터 역순으로 연속 날짜 카운팅
   - 날짜 차이가 1일이면 streak++
   - 1일 초과면 break
```

- 오늘 공부 안 해도 어제까지 streak이 유지됨 (오늘 or 어제 조건)
- 예: 4/14, 4/15, 4/16 연속 공부 → 4/17 아직 안 함 → streak = 3

---

## DB 테이블 참고

### timer_sessions

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | VARCHAR(36) (PK) | 서버 생성 UUID |
| `user_id` | BIGINT (FK → users) | 소유자 |
| `todo_id` | VARCHAR(36) | 연결된 Todo ID (nullable) |
| `todo_title` | VARCHAR(100) | Todo 제목 스냅샷 (nullable) |
| `started_at` | TIMESTAMP | 시작 시각 |
| `ended_at` | TIMESTAMP | 종료 시각 |
| `duration_minutes` | INTEGER | 실제 공부 시간 (분) |
| `created_at` | TIMESTAMP | 레코드 생성일 |
