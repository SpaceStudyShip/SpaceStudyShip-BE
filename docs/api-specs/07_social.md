# 07. Social (친구 + 랭킹)

> Base Path: `/api/friends`
> 엔드포인트: 7개
> 동기화: **Tier 3 (Server-Only)**
> 공통 규칙: [00_common.md](./00_common.md) 참조

---

## 동기화 전략

Social은 **Server-Only** 방식입니다.

```
1. 항상 서버 API 호출
2. 응답 데이터를 로컬 캐시에 저장 (읽기 전용)
3. 오프라인 시 캐시 데이터 표시 + "오프라인" 배지
```

친구 상태, 랭킹 등은 실시간성이 중요하므로 항상 서버에서 최신 데이터를 가져옵니다.

---

## 엔드포인트 요약

| # | Method | Path | 설명 |
|---|--------|------|------|
| 1 | GET | `/api/friends` | 친구 목록 조회 |
| 2 | POST | `/api/friends/request` | 친구 요청 보내기 |
| 3 | GET | `/api/friends/requests/received` | 받은 친구 요청 목록 |
| 4 | POST | `/api/friends/requests/{requestId}/accept` | 친구 요청 수락 |
| 5 | POST | `/api/friends/requests/{requestId}/reject` | 친구 요청 거절 |
| 6 | DELETE | `/api/friends/{friendUserId}` | 친구 삭제 |
| 7 | GET | `/api/friends/ranking` | 친구 랭킹 조회 |

---

## 친구 상태 값

| status | 설명 | 판별 기준 |
|--------|------|----------|
| `STUDYING` | 현재 공부 중 | 타이머가 실행 중인 상태 |
| `IDLE` | 앱 열려 있지만 타이머 미실행 | 최근 N분 이내 API 호출 이력 있음 |
| `OFFLINE` | 앱 미사용 | 최근 API 호출 이력 없음 |

### 상태 판별 로직 (서버)

```
마지막 API 호출 시각 기준:
- 타이머 실행 중 → STUDYING
- 5분 이내 → IDLE
- 5분 초과 → OFFLINE
```

클라이언트에서 주기적으로 heartbeat API를 호출하거나, API 호출 시 자동으로 `last_active_at`을 갱신하여 판별합니다.

---

## 1. 친구 목록 조회

`GET /api/friends`

현재 유저의 친구 목록과 실시간 상태를 반환합니다.

### 인증: 필요

### Query Parameters: 없음

### Response

**200 OK**

```json
[
  {
    "userId": 10,
    "nickname": "김우주",
    "status": "STUDYING",
    "studyDurationMinutes": 155,
    "currentSubject": "수학",
    "weeklyStudyDurationMinutes": 980
  },
  {
    "userId": 11,
    "nickname": "박탐험",
    "status": "STUDYING",
    "studyDurationMinutes": 72,
    "currentSubject": "영어",
    "weeklyStudyDurationMinutes": 705
  },
  {
    "userId": 12,
    "nickname": "최성운",
    "status": "IDLE",
    "studyDurationMinutes": null,
    "currentSubject": null,
    "weeklyStudyDurationMinutes": 330
  },
  {
    "userId": 13,
    "nickname": "한은하",
    "status": "OFFLINE",
    "studyDurationMinutes": null,
    "currentSubject": null,
    "weeklyStudyDurationMinutes": 135
  }
]
```

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| `userId` | Long | X | 친구의 유저 ID |
| `nickname` | String | X | 닉네임 |
| `status` | String | X | 현재 상태 (`STUDYING`, `IDLE`, `OFFLINE`) |
| `studyDurationMinutes` | Integer | O | 현재 공부 세션 경과 시간 (분). STUDYING일 때만 값 있음 |
| `currentSubject` | String | O | 현재 공부 과목 (Todo 제목). STUDYING일 때만 값 있음 |
| `weeklyStudyDurationMinutes` | Integer | X | 이번 주 총 공부 시간 (분, 월요일~일요일) |

정렬: `status` (STUDYING → IDLE → OFFLINE) → `weeklyStudyDurationMinutes` 내림차순

### 서버 처리

```
1. 현재 유저의 친구 관계 조회 (friendships 테이블)
2. 각 친구에 대해:
   a. 활성 타이머 세션 확인 → STUDYING / IDLE / OFFLINE 판별
   b. STUDYING이면: 현재 세션의 경과 시간, Todo 제목 조회
   c. 이번 주 공부 시간 합산
3. 정렬 후 반환
```

---

## 2. 친구 요청 보내기

`POST /api/friends/request`

닉네임으로 친구 요청을 보냅니다.

### 인증: 필요

### Request Body

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `targetNickname` | String | O | 친구 추가할 대상의 닉네임 |

```json
{
  "targetNickname": "김우주"
}
```

### Response

**201 Created**

```json
{
  "requestId": 1,
  "targetUserId": 42,
  "targetNickname": "김우주",
  "status": "PENDING",
  "createdAt": "2026-04-16T11:00:00Z"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `requestId` | Long | 친구 요청 ID (수락/거절 시 사용) |
| `targetUserId` | Long | 대상 유저 ID |
| `targetNickname` | String | 대상 닉네임 |
| `status` | String | 요청 상태 (`PENDING`) |
| `createdAt` | String | 요청 시각 |

### Error

| Status | code | 상황 |
|--------|------|------|
| 400 | `SELF_REQUEST` | 자기 자신에게 요청 |
| 404 | `USER_NOT_FOUND` | 닉네임에 해당하는 유저 없음 |
| 409 | `ALREADY_FRIENDS` | 이미 친구 관계 |
| 409 | `REQUEST_ALREADY_SENT` | 이미 보낸 요청이 PENDING 상태 |
| 409 | `REQUEST_ALREADY_RECEIVED` | 상대방이 이미 나에게 요청을 보낸 상태 |

### REQUEST_ALREADY_RECEIVED 처리

상대가 이미 나에게 요청을 보낸 경우, 클라이언트에서 "상대방이 이미 친구 요청을 보냈어요. 수락하시겠어요?" 안내를 표시하고, 해당 요청을 수락하도록 유도합니다.

---

## 3. 받은 친구 요청 목록

`GET /api/friends/requests/received`

다른 유저가 나에게 보낸 대기 중인 친구 요청 목록을 조회합니다.

### 인증: 필요

### Query Parameters: 없음

### Response

**200 OK**

```json
[
  {
    "requestId": 1,
    "fromUserId": 10,
    "fromNickname": "박탐험",
    "status": "PENDING",
    "createdAt": "2026-04-15T20:00:00Z"
  },
  {
    "requestId": 2,
    "fromUserId": 15,
    "fromNickname": "이별자리",
    "status": "PENDING",
    "createdAt": "2026-04-16T09:30:00Z"
  }
]
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `requestId` | Long | 요청 ID |
| `fromUserId` | Long | 요청 보낸 유저 ID |
| `fromNickname` | String | 요청 보낸 유저 닉네임 |
| `status` | String | `PENDING` |
| `createdAt` | String | 요청 시각 |

정렬: `createdAt` 내림차순 (최신순)

PENDING 상태의 요청만 반환합니다 (이미 수락/거절된 요청은 제외).

---

## 4. 친구 요청 수락

`POST /api/friends/requests/{requestId}/accept`

친구 요청을 수락하여 양방향 친구 관계를 생성합니다.

### 인증: 필요

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `requestId` | Long | 수락할 친구 요청 ID |

### Request Body: 없음

### Response

**200 OK**

```json
{
  "friendUserId": 10,
  "friendNickname": "박탐험"
}
```

### Error

| Status | code | 상황 |
|--------|------|------|
| 404 | `REQUEST_NOT_FOUND` | requestId에 해당하는 요청 없음 |
| 400 | `REQUEST_NOT_PENDING` | 이미 수락/거절된 요청 |
| 403 | `FORBIDDEN` | 본인에게 온 요청이 아님 |

### 서버 처리

```
BEGIN TRANSACTION;
  1. 요청 상태를 ACCEPTED로 변경
  2. 양방향 친구 관계 생성:
     - friendships (user_id=요청자, friend_user_id=수신자)
     - friendships (user_id=수신자, friend_user_id=요청자)
COMMIT;
```

---

## 5. 친구 요청 거절

`POST /api/friends/requests/{requestId}/reject`

친구 요청을 거절합니다.

### 인증: 필요

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `requestId` | Long | 거절할 친구 요청 ID |

### Request Body: 없음

### Response

**204 No Content**

### Error

| Status | code | 상황 |
|--------|------|------|
| 404 | `REQUEST_NOT_FOUND` | requestId에 해당하는 요청 없음 |
| 400 | `REQUEST_NOT_PENDING` | 이미 수락/거절된 요청 |
| 403 | `FORBIDDEN` | 본인에게 온 요청이 아님 |

### 서버 처리

요청 상태를 `REJECTED`로 변경. 거절된 요청은 재전송 가능 (별도 cooldown 없음).

---

## 6. 친구 삭제

`DELETE /api/friends/{friendUserId}`

친구 관계를 양방향으로 삭제합니다.

### 인증: 필요

### Path Parameters

| 파라미터 | 타입 | 설명 |
|---------|------|------|
| `friendUserId` | Long | 삭제할 친구의 유저 ID |

### Request Body: 없음

### Response

**204 No Content**

### Error

| Status | code | 상황 |
|--------|------|------|
| 404 | `FRIEND_NOT_FOUND` | 해당 유저와 친구 관계가 아님 |

### 서버 처리

```
BEGIN TRANSACTION;
  1. 양방향 친구 관계 삭제:
     DELETE FROM friendships WHERE (user_id=:me AND friend_user_id=:target)
        OR (user_id=:target AND friend_user_id=:me);
  2. 관련 PENDING 요청이 있으면 CANCELLED로 변경
COMMIT;
```

상대방에게 삭제 알림은 보내지 않습니다 (친구 목록에서 조용히 사라짐).

---

## 7. 친구 랭킹 조회

`GET /api/friends/ranking`

나와 내 친구들의 공부 시간 랭킹을 조회합니다.
정렬 기준: 공부 시간 내림차순.

### 인증: 필요

### Query Parameters

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| `period` | String | X | `DAILY` | 랭킹 기간 |

**period 허용 값:**

| 값 | 설명 | 기간 |
|----|------|------|
| `DAILY` | 일간 랭킹 | 오늘 00:00 ~ 현재 |
| `WEEKLY` | 주간 랭킹 | 이번 주 월요일 00:00 ~ 현재 |
| `MONTHLY` | 월간 랭킹 | 이번 달 1일 00:00 ~ 현재 |

```
GET /api/friends/ranking
GET /api/friends/ranking?period=WEEKLY
GET /api/friends/ranking?period=MONTHLY
```

### Response

**200 OK**

```json
{
  "period": "WEEKLY",
  "myRank": 4,
  "rankings": [
    {
      "rank": 1,
      "userId": 10,
      "nickname": "김우주",
      "studyDurationMinutes": 5670,
      "isCurrentUser": false
    },
    {
      "rank": 2,
      "userId": 11,
      "nickname": "박탐험",
      "studyDurationMinutes": 4315,
      "isCurrentUser": false
    },
    {
      "rank": 3,
      "userId": 12,
      "nickname": "이별자리",
      "studyDurationMinutes": 3850,
      "isCurrentUser": false
    },
    {
      "rank": 4,
      "userId": 1,
      "nickname": "나",
      "studyDurationMinutes": 3300,
      "isCurrentUser": true
    },
    {
      "rank": 5,
      "userId": 13,
      "nickname": "최성운",
      "studyDurationMinutes": 2100,
      "isCurrentUser": false
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `period` | String | 요청한 기간 |
| `myRank` | Integer | 내 순위 (1-indexed). 친구가 없으면 1 |
| `rankings[].rank` | Integer | 순위 |
| `rankings[].userId` | Long | 유저 ID |
| `rankings[].nickname` | String | 닉네임 |
| `rankings[].studyDurationMinutes` | Integer | 해당 기간 총 공부 시간 (분) |
| `rankings[].isCurrentUser` | Boolean | 현재 유저 본인 여부 |

### 동점 처리

공부 시간이 동일한 경우 `userId` 오름차순으로 정렬합니다 (가입 순).

### 랭킹 대상

나 + 내 친구들만 포함 (전체 유저 랭킹 아님).
친구가 0명이면 나만 포함된 리스트를 반환합니다.

### 서버 처리

```sql
-- 주간 랭킹 예시
SELECT
  u.id AS user_id,
  u.nickname,
  COALESCE(SUM(ts.duration_minutes), 0) AS study_duration_minutes
FROM users u
LEFT JOIN timer_sessions ts
  ON u.id = ts.user_id
  AND ts.started_at >= :weekStart
WHERE u.id = :myUserId
   OR u.id IN (SELECT friend_user_id FROM friendships WHERE user_id = :myUserId)
GROUP BY u.id, u.nickname
ORDER BY study_duration_minutes DESC, u.id ASC;
```

---

## DB 테이블 참고

### friend_requests

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT (PK, AUTO_INCREMENT) | 요청 ID |
| `from_user_id` | BIGINT (FK → users) | 요청 보낸 유저 |
| `to_user_id` | BIGINT (FK → users) | 요청 받은 유저 |
| `status` | VARCHAR(10) | PENDING / ACCEPTED / REJECTED / CANCELLED |
| `created_at` | TIMESTAMP | 요청 시각 |
| `updated_at` | TIMESTAMP | 상태 변경 시각 |

UNIQUE 제약: (`from_user_id`, `to_user_id`, `status`='PENDING')
- 같은 상대에게 PENDING 요청은 하나만 존재 가능

### friendships

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT (PK) | |
| `user_id` | BIGINT (FK → users) | 유저 A |
| `friend_user_id` | BIGINT (FK → users) | 유저 B |
| `created_at` | TIMESTAMP | 친구 관계 생성 시각 |

UNIQUE 제약: (`user_id`, `friend_user_id`)
양방향 저장: A→B, B→A 두 레코드

### 인덱스

```sql
CREATE INDEX idx_friendships_user_id ON friendships(user_id);
CREATE INDEX idx_friend_requests_to_user ON friend_requests(to_user_id, status);
```
