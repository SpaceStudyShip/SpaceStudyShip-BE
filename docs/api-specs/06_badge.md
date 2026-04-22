# 06. Badge (배지)

> Base Path: `/api/badges`
> 엔드포인트: 2개
> 동기화: **Tier 2 (Server-Validated)**
> 공통 규칙: [00_common.md](./00_common.md) 참조

---

## 배지 시스템 개요

배지는 사용자의 학습 마일스톤을 기록하는 업적 시스템입니다.

### 해금 흐름

```
1. 사용자가 공부 세션 완료 / 탐험 해금 등의 활동 수행
2. 클라이언트가 POST /api/badges/check 호출
3. 서버에서 현재 유저의 통계 기반으로 해금 가능한 배지 확인
4. 조건 충족 시 자동 해금 → 새로 해금된 배지 목록 반환
5. 클라이언트에서 해금 축하 UI 표시
```

### 해금 조건 카테고리

| category | 기준 데이터 | 예시 |
|----------|----------|------|
| `STUDY_TIME` | 누적 공부 시간 (분) | "누적 60분 공부" → requiredValue=60 |
| `STREAK` | 연속 공부 일수 | "7일 연속 공부" → requiredValue=7 |
| `SESSION` | 총 세션 수 | "100회 공부 완료" → requiredValue=100 |
| `EXPLORATION` | 탐험 진행 | "지구 행성 클리어" (description으로 조건 표시) |
| `FUEL` | 누적 연료 충전량 | "연료 1000 충전" → requiredValue=1000 |
| `HIDDEN` | 숨겨진 조건 | 조건 비공개, 힌트 없음 |

### 배지 희귀도

| rarity | 설명 | UI 표현 |
|--------|------|--------|
| `NORMAL` | 일반 배지 | 기본 테두리 |
| `RARE` | 희귀 배지 | 파란 테두리 |
| `EPIC` | 영웅 배지 | 보라 테두리 |
| `LEGENDARY` | 전설 배지 | 금색 테두리 |
| `HIDDEN` | 숨겨진 배지 | 잠금 시 아이콘/이름/설명 모두 "???" |

---

## 엔드포인트 요약

| # | Method | Path | 설명 |
|---|--------|------|------|
| 1 | GET | `/api/badges` | 배지 목록 조회 |
| 2 | POST | `/api/badges/check` | 배지 해금 확인 |

---

## 배지 객체 구조

```json
{
  "id": "badge-first-hour",
  "name": "첫 번째 시간",
  "icon": "clock_1h",
  "description": "누적 공부 시간 1시간 달성",
  "category": "STUDY_TIME",
  "rarity": "NORMAL",
  "requiredValue": 60,
  "isUnlocked": true,
  "unlockedAt": "2026-04-05T10:00:00Z"
}
```

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| `id` | String | X | 배지 고유 ID (시드 데이터, 고정 문자열) |
| `name` | String | X | 배지 이름 (HIDDEN 미해금 시 `"???"`) |
| `icon` | String | X | 아이콘 식별자 (HIDDEN 미해금 시 `"locked"`) |
| `description` | String | X | 배지 설명 (HIDDEN 미해금 시 `"???"`) |
| `category` | String | X | 해금 조건 카테고리 |
| `rarity` | String | X | 희귀도 |
| `requiredValue` | Integer | X | 해금에 필요한 조건값 (HIDDEN 미해금 시 `0`) |
| `isUnlocked` | Boolean | X | 해금 여부 |
| `unlockedAt` | String | O | 해금 시각 (null = 미해금) |

### HIDDEN 배지의 잠금 상태 응답

```json
{
  "id": "badge-hidden-001",
  "name": "???",
  "icon": "locked",
  "description": "???",
  "category": "HIDDEN",
  "rarity": "HIDDEN",
  "requiredValue": 0,
  "isUnlocked": false,
  "unlockedAt": null
}
```

---

## 1. 배지 목록 조회

`GET /api/badges`

전체 배지 목록과 사용자의 해금 상태를 반환합니다.
HIDDEN 배지는 미해금 시 정보가 마스킹되어 반환됩니다.

### 인증: 필요

### Query Parameters

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| `category` | String | X | 전체 | 카테고리 필터 |
| `unlockedOnly` | Boolean | X | false | `true`: 해금된 배지만 반환 |

**category 허용 값**: `STUDY_TIME`, `STREAK`, `SESSION`, `EXPLORATION`, `FUEL`, `HIDDEN`

```
GET /api/badges
GET /api/badges?category=STUDY_TIME
GET /api/badges?unlockedOnly=true
GET /api/badges?category=STREAK&unlockedOnly=true
```

### Response

**200 OK**

```json
[
  {
    "id": "badge-first-hour",
    "name": "첫 번째 시간",
    "icon": "clock_1h",
    "description": "누적 공부 시간 1시간 달성",
    "category": "STUDY_TIME",
    "rarity": "NORMAL",
    "requiredValue": 60,
    "isUnlocked": true,
    "unlockedAt": "2026-04-05T10:00:00Z"
  },
  {
    "id": "badge-streak-7",
    "name": "일주일 연속",
    "icon": "flame_7d",
    "description": "7일 연속 공부 달성",
    "category": "STREAK",
    "rarity": "RARE",
    "requiredValue": 7,
    "isUnlocked": false,
    "unlockedAt": null
  },
  {
    "id": "badge-hidden-001",
    "name": "???",
    "icon": "locked",
    "description": "???",
    "category": "HIDDEN",
    "rarity": "HIDDEN",
    "requiredValue": 0,
    "isUnlocked": false,
    "unlockedAt": null
  }
]
```

정렬: `category` → `rarity` → `requiredValue` 오름차순

---

## 2. 배지 해금 확인

`POST /api/badges/check`

서버에서 현재 사용자의 통계를 기반으로 해금 조건을 만족하는 배지를 확인하고 자동으로 해금합니다.

### 인증: 필요

### Request Body: 없음

### 호출 시점 (클라이언트)

| 이벤트 | 설명 |
|--------|------|
| 타이머 세션 완료 후 | STUDY_TIME, SESSION, STREAK 배지 확인 |
| 탐험 해금 후 | EXPLORATION 배지 확인 |
| 앱 실행 시 | 누적 통계 변경 확인 (선택적) |

### Response

**200 OK**

```json
{
  "newlyUnlocked": [
    {
      "id": "badge-streak-7",
      "name": "일주일 연속",
      "icon": "flame_7d",
      "description": "7일 연속 공부 달성",
      "category": "STREAK",
      "rarity": "RARE",
      "unlockedAt": "2026-04-16T10:30:00Z"
    }
  ]
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `newlyUnlocked` | Array | 이번 호출로 새로 해금된 배지 목록 (빈 배열이면 새로 해금된 배지 없음) |

### 서버 처리 로직

```
1. 유저의 현재 통계 조회:
   - 누적 공부 시간 (분): SUM(timer_sessions.duration_minutes)
   - 연속 공부 일수: streak 계산
   - 총 세션 수: COUNT(timer_sessions)
   - 누적 연료 충전량: user_fuel.total_charged
   - 탐험 진행 상태: user_exploration_progress

2. 아직 미해금인 배지 목록 조회

3. 각 배지에 대해 해금 조건 확인:
   - STUDY_TIME: 누적 공부 시간 >= requiredValue (분)
   - STREAK: 연속 일수 >= requiredValue
   - SESSION: 총 세션 수 >= requiredValue
   - FUEL: 누적 충전량 >= requiredValue
   - EXPLORATION: 특정 노드 클리어 여부 확인
   - HIDDEN: 각 배지별 커스텀 조건

4. 조건 충족 배지 해금 처리 (user_badge_progress INSERT)
5. 새로 해금된 배지 목록 반환
```

---

## DB 테이블 참고

### badges (시드 데이터, 읽기 전용)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | VARCHAR(50) (PK) | 배지 ID |
| `name` | VARCHAR(50) | 배지 이름 |
| `icon` | VARCHAR(50) | 아이콘 식별자 |
| `description` | VARCHAR(200) | 설명 |
| `category` | VARCHAR(20) | 해금 조건 카테고리 |
| `rarity` | VARCHAR(10) | 희귀도 |
| `required_value` | INTEGER | 해금 조건값 |

### user_badge_progress (유저별 해금 상태)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | BIGINT (PK) | |
| `user_id` | BIGINT (FK → users) | 유저 ID |
| `badge_id` | VARCHAR(50) (FK → badges) | 배지 ID |
| `unlocked_at` | TIMESTAMP | 해금 시각 |
| `is_new` | BOOLEAN | 신규 해금 표시 (클라이언트가 확인 후 false) |

UNIQUE 제약: (`user_id`, `badge_id`)

### 시드 데이터 예시

```sql
INSERT INTO badges (id, name, icon, description, category, rarity, required_value) VALUES
('badge-first-10min', '첫 걸음', 'footprint', '첫 10분 공부 완료', 'STUDY_TIME', 'NORMAL', 10),
('badge-first-hour', '첫 번째 시간', 'clock_1h', '누적 공부 시간 1시간 달성', 'STUDY_TIME', 'NORMAL', 60),
('badge-10-hours', '10시간의 노력', 'clock_10h', '누적 공부 시간 10시간 달성', 'STUDY_TIME', 'RARE', 600),
('badge-100-hours', '100시간의 여정', 'clock_100h', '누적 공부 시간 100시간 달성', 'STUDY_TIME', 'EPIC', 6000),
('badge-streak-3', '3일 연속', 'flame_3d', '3일 연속 공부 달성', 'STREAK', 'NORMAL', 3),
('badge-streak-7', '일주일 연속', 'flame_7d', '7일 연속 공부 달성', 'STREAK', 'RARE', 7),
('badge-streak-30', '한 달 연속', 'flame_30d', '30일 연속 공부 달성', 'STREAK', 'LEGENDARY', 30),
('badge-session-10', '10번째 세션', 'counter_10', '공부 세션 10회 완료', 'SESSION', 'NORMAL', 10),
('badge-session-100', '100번째 세션', 'counter_100', '공부 세션 100회 완료', 'SESSION', 'RARE', 100),
('badge-earth-clear', '지구 마스터', 'earth_gold', '지구 행성 클리어', 'EXPLORATION', 'EPIC', 1);
```
