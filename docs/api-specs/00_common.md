# 00. 공통 사항

> **Space Study Ship** 백엔드 API 공통 규칙
> **Version:** 1.0.0
> **Base URL:** `https://api.spacestudyship.com`
> **Date:** 2026-04-17

---

## 문서 목록

| 파일 | 카테고리 | 엔드포인트 수 |
|------|---------|------------|
| [01_auth.md](./01_auth.md) | Auth (인증 + 프로필) | 6개 |
| [02_todo.md](./02_todo.md) | Todo (할 일 + 카테고리) | 8개 |
| [03_timer.md](./03_timer.md) | Timer (공부 타이머) | 3개 |
| [04_fuel.md](./04_fuel.md) | Fuel (연료) | 2개 |
| [05_exploration.md](./05_exploration.md) | Exploration (탐험) | 4개 |
| [06_badge.md](./06_badge.md) | Badge (배지) | 2개 |
| [07_social.md](./07_social.md) | Social (친구 + 랭킹) | 7개 |
| **합계** | | **32개** |

---

## 인증

| 항목 | 값 |
|------|-----|
| 인증 방식 | JWT Bearer Token |
| 헤더 | `Authorization: Bearer {accessToken}` |
| Access Token 위치 | HTTP Header (Authorization) |
| Refresh Token 위치 | Request Body (로그아웃, 재발급 시) |

### 공개 API (토큰 불필요)

아래 엔드포인트는 `Authorization` 헤더 없이 호출 가능합니다.

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/auth/login` | 소셜 로그인 |
| POST | `/api/auth/reissue` | 토큰 재발급 |

### 토큰 만료 시 처리 흐름

```
1. 클라이언트가 API 호출
2. 서버가 401 UNAUTHORIZED 응답
3. 클라이언트가 POST /api/auth/reissue 호출 (refreshToken 전송)
4-a. 성공 (200): 새 accessToken + refreshToken 저장 후 원래 API 재시도
4-b. 실패 (401 INVALID_REFRESH_TOKEN): 로그아웃 처리 → 로그인 화면 이동
```

---

## 공통 응답 형식

### 성공 응답

- 조회 성공: `200 OK`
- 생성 성공: `201 Created`
- 삭제 성공: `204 No Content` (응답 본문 없음)
- 수정 성공: `200 OK`

### 에러 응답

모든 에러는 아래 형식으로 응답합니다.

```json
{
  "code": "ERROR_CODE",
  "message": "사람이 읽을 수 있는 메시지"
}
```

### 공통 HTTP Status Code

| HTTP Status | code | 설명 | 클라이언트 처리 |
|-------------|------|------|--------------|
| 400 | `BAD_REQUEST` | 요청 파라미터 오류 | 입력값 확인 후 재시도 |
| 401 | `UNAUTHORIZED` | 토큰 없음 또는 만료 | 토큰 재발급 시도 |
| 403 | `FORBIDDEN` | 권한 없음 | 접근 제한 안내 |
| 404 | `NOT_FOUND` | 리소스 없음 | 목록으로 이동 또는 안내 |
| 409 | `CONFLICT` | 중복 (닉네임, 친구 요청 등) | 중복 안내 메시지 표시 |
| 500 | `INTERNAL_ERROR` | 서버 내부 오류 | "잠시 후 다시 시도해주세요" 표시 |

---

## 공통 규칙

### 날짜/시간 형식

| 용도 | 형식 | 예시 |
|------|------|------|
| 날짜+시간 (타임스탬프) | ISO 8601 UTC | `2026-04-16T09:30:00Z` |
| 날짜만 (일정, 완료일) | YYYY-MM-DD | `2026-04-16` |

- 모든 타임스탬프는 **UTC** 기준으로 저장/응답
- 클라이언트에서 로컬 타임존으로 변환하여 표시

### ID 타입

| 생성 주체 | 타입 | 적용 대상 |
|----------|------|----------|
| 서버 생성 | `Long` (auto increment) | userId, requestId |
| 서버 생성 | `String (UUID v4)` | sessionId (타이머 세션) |
| 클라이언트 생성 | `String (UUID v4)` | todoId, categoryId |
| 시드 데이터 | `String` (고정 ID) | badgeId, planetId, regionId |

### 페이지네이션

대량 데이터를 반환하는 API에 적용됩니다.

**Request:**

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|-------|------|
| `page` | Integer | 0 | 페이지 번호 (0-indexed) |
| `size` | Integer | 20 | 페이지당 항목 수 (최대 100) |

**Response:**

```json
{
  "content": [...],
  "page": 0,
  "size": 20,
  "totalElements": 120,
  "totalPages": 6
}
```

**적용 대상:** Timer 세션 목록, Fuel 거래 내역

### Duration (시간 단위)

- 모든 시간 관련 필드는 **분 단위 정수** (Integer, minutes)
- 예: `durationMinutes: 90` = 1시간 30분
- 클라이언트에서 "1시간 30분" 등으로 변환하여 표시

### 연료 규칙

- 연료 충전: 별도 API 없음. `POST /api/timer-sessions` 시 서버에서 자동 충전
- 연료 소비: 탐험 해금 API(`POST /api/explorations/.../unlock`) 내부에서 자동 차감
- 연료 잔량: `GET /api/fuel`로 조회

---

## 데이터 동기화 전략

| Tier | 전략 | 적용 API | 설명 |
|------|------|---------|------|
| **Tier 1** | Optimistic Updates | Todo CRUD | 로컬에 먼저 저장 → UI 즉시 반영 → 백그라운드 서버 동기화 |
| **Tier 2** | Server-Validated | Timer 세션, Fuel, Exploration, Badge | 서버에서 검증/계산 후 확정값 반환. 온라인 필수 |
| **Tier 3** | Server-Only | 친구 목록, 랭킹 | 항상 서버에서 조회. 로컬 캐시는 읽기 전용 (오프라인 시 캐시 표시) |

---

## 엔드포인트 전체 목록

| # | Method | Path | 설명 | Tier |
|---|--------|------|------|------|
| | | **Auth** | | |
| 1 | POST | `/api/auth/login` | 소셜 로그인 | - |
| 2 | POST | `/api/auth/logout` | 로그아웃 | - |
| 3 | POST | `/api/auth/reissue` | 토큰 재발급 | - |
| 4 | DELETE | `/api/auth/withdraw` | 회원 탈퇴 | - |
| 5 | GET | `/api/auth/check-nickname` | 닉네임 중복 확인 | - |
| 6 | PATCH | `/api/auth/nickname` | 닉네임 변경 | - |
| | | **Todo** | | |
| 7 | GET | `/api/todos` | Todo 목록 조회 | Tier 1 |
| 8 | POST | `/api/todos` | Todo 생성 | Tier 1 |
| 9 | PATCH | `/api/todos/{todoId}` | Todo 수정 | Tier 1 |
| 10 | DELETE | `/api/todos/{todoId}` | Todo 삭제 | Tier 1 |
| 11 | GET | `/api/todo-categories` | 카테고리 목록 | Tier 1 |
| 12 | POST | `/api/todo-categories` | 카테고리 생성 | Tier 1 |
| 13 | PATCH | `/api/todo-categories/{categoryId}` | 카테고리 수정 | Tier 1 |
| 14 | DELETE | `/api/todo-categories/{categoryId}` | 카테고리 삭제 | Tier 1 |
| | | **Timer** | | |
| 15 | GET | `/api/timer-sessions` | 세션 목록 조회 | Tier 2 |
| 16 | POST | `/api/timer-sessions` | 세션 기록 저장 | Tier 2 |
| 17 | GET | `/api/timer-sessions/today-stats` | 오늘 통계 | Tier 2 |
| | | **Fuel** | | |
| 18 | GET | `/api/fuel` | 연료 잔량 조회 | Tier 2 |
| 19 | GET | `/api/fuel/transactions` | 거래 내역 조회 | Tier 2 |
| | | **Exploration** | | |
| 20 | GET | `/api/explorations/planets` | 행성 목록 | Tier 2 |
| 21 | GET | `/api/explorations/planets/{planetId}/regions` | 지역 목록 | Tier 2 |
| 22 | POST | `/api/explorations/regions/{regionId}/unlock` | 지역 해금 | Tier 2 |
| 23 | POST | `/api/explorations/planets/{planetId}/unlock` | 행성 해금 | Tier 2 |
| | | **Badge** | | |
| 24 | GET | `/api/badges` | 배지 목록 | Tier 2 |
| 25 | POST | `/api/badges/check` | 배지 해금 확인 | Tier 2 |
| | | **Social** | | |
| 26 | GET | `/api/friends` | 친구 목록 | Tier 3 |
| 27 | POST | `/api/friends/request` | 친구 요청 | Tier 3 |
| 28 | GET | `/api/friends/requests/received` | 받은 요청 목록 | Tier 3 |
| 29 | POST | `/api/friends/requests/{requestId}/accept` | 요청 수락 | Tier 3 |
| 30 | POST | `/api/friends/requests/{requestId}/reject` | 요청 거절 | Tier 3 |
| 31 | DELETE | `/api/friends/{friendUserId}` | 친구 삭제 | Tier 3 |
| 32 | GET | `/api/friends/ranking` | 친구 랭킹 | Tier 3 |
