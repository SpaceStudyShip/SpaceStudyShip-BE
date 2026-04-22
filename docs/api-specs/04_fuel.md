# 04. Fuel (연료)

> Base Path: `/api/fuel`
> 엔드포인트: 2개
> 동기화: **Tier 2 (Server-Validated)**
> 공통 규칙: [00_common.md](./00_common.md) 참조

---

## 연료 시스템 개요

연료는 공부의 보상이자 탐험의 비용입니다.

```
공부(타이머) → 연료 충전 → 탐험(행성/지역 해금)에 소비
```

### 핵심 규칙

- **충전**: `POST /api/timer-sessions` 시 서버에서 자동 충전 (별도 충전 API 없음)
- **소비**: `POST /api/explorations/.../unlock` 시 서버에서 자동 차감 (별도 소비 API 없음)
- **조회**: 이 문서의 API로 잔량 및 거래 내역 조회
- **비율**: 1분 공부 = 1 연료

---

## 엔드포인트 요약

| # | Method | Path | 설명 |
|---|--------|------|------|
| 1 | GET | `/api/fuel` | 연료 잔량 조회 |
| 2 | GET | `/api/fuel/transactions` | 거래 내역 조회 |

---

## 연료 잔량 객체 구조

```json
{
  "currentFuel": 350,
  "totalCharged": 1200,
  "totalConsumed": 850,
  "pendingMinutes": 0,
  "lastUpdatedAt": "2026-04-16T10:30:00Z"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `currentFuel` | Integer | 현재 보유 연료 (`totalCharged - totalConsumed`) |
| `totalCharged` | Integer | 누적 충전량 |
| `totalConsumed` | Integer | 누적 소비량 |
| `pendingMinutes` | Integer | 아직 서버에 동기화되지 않은 공부 시간 (분). 현재 사용 안 함, 향후 확장용 |
| `lastUpdatedAt` | String | 마지막 연료 변동 시각 (ISO 8601 UTC) |

---

## 거래 내역 객체 구조

```json
{
  "id": "tx-uuid-1234",
  "type": "charge",
  "amount": 90,
  "reason": "STUDY_SESSION",
  "referenceId": "session-uuid-5678",
  "balanceAfter": 350,
  "createdAt": "2026-04-16T10:30:00Z"
}
```

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| `id` | String (UUID) | X | 거래 고유 ID |
| `type` | String | X | `"charge"` (충전) 또는 `"consume"` (소비) |
| `amount` | Integer | X | 거래 연료량 (항상 양수) |
| `reason` | String | X | 거래 사유 (아래 표 참조) |
| `referenceId` | String | O | 관련 리소스 ID (세션 ID, 지역 ID 등) |
| `balanceAfter` | Integer | X | 거래 후 잔량 |
| `createdAt` | String | X | 거래 시각 (ISO 8601 UTC) |

### reason 값

| reason | type | 설명 | referenceId |
|--------|------|------|------------|
| `STUDY_SESSION` | charge | 공부 세션 완료로 충전 | 타이머 세션 ID |
| `EXPLORATION_UNLOCK` | consume | 행성/지역 해금으로 소비 | 해금된 행성/지역 ID |

---

## 1. 연료 잔량 조회

`GET /api/fuel`

현재 유저의 연료 잔량 및 누적 충전/소비량을 조회합니다.

### 인증: 필요

### Query Parameters: 없음

### Response

**200 OK**

```json
{
  "currentFuel": 350,
  "totalCharged": 1200,
  "totalConsumed": 850,
  "pendingMinutes": 0,
  "lastUpdatedAt": "2026-04-16T10:30:00Z"
}
```

### 신규 유저

가입 직후 유저는 모든 값이 0입니다.

```json
{
  "currentFuel": 0,
  "totalCharged": 0,
  "totalConsumed": 0,
  "pendingMinutes": 0,
  "lastUpdatedAt": "2026-04-16T00:00:00Z"
}
```

---

## 2. 연료 거래 내역 조회

`GET /api/fuel/transactions`

연료 충전/소비 이력을 조회합니다.

### 인증: 필요

### Query Parameters

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|---------|------|------|-------|------|
| `type` | String | X | 전체 | `"charge"`: 충전만, `"consume"`: 소비만 |
| `startDate` | String | X | - | 시작일 이후 내역 (`YYYY-MM-DD`) |
| `endDate` | String | X | - | 종료일 이전 내역 (`YYYY-MM-DD`) |
| `page` | Integer | X | 0 | 페이지 번호 |
| `size` | Integer | X | 20 | 페이지당 항목 수 (최대 100) |

```
GET /api/fuel/transactions
GET /api/fuel/transactions?type=charge
GET /api/fuel/transactions?type=consume&startDate=2026-04-01&endDate=2026-04-16
```

### Response

**200 OK**

```json
{
  "content": [
    {
      "id": "tx-uuid-1",
      "type": "charge",
      "amount": 90,
      "reason": "STUDY_SESSION",
      "referenceId": "session-uuid-1",
      "balanceAfter": 350,
      "createdAt": "2026-04-16T10:30:00Z"
    },
    {
      "id": "tx-uuid-2",
      "type": "consume",
      "amount": 100,
      "reason": "EXPLORATION_UNLOCK",
      "referenceId": "region-kr",
      "balanceAfter": 260,
      "createdAt": "2026-04-16T09:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 120,
  "totalPages": 6
}
```

정렬: `createdAt` 내림차순 (최신순)

---

## DB 테이블 참고

### user_fuel

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `user_id` | BIGINT (PK, FK → users) | 유저 ID |
| `current_fuel` | INTEGER | 현재 잔량 |
| `total_charged` | INTEGER | 누적 충전량 |
| `total_consumed` | INTEGER | 누적 소비량 |
| `pending_minutes` | INTEGER | 미동기화 시간 |
| `last_updated_at` | TIMESTAMP | 마지막 변동 시각 |

### fuel_transactions

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | VARCHAR(36) (PK) | 거래 UUID |
| `user_id` | BIGINT (FK → users) | 유저 ID |
| `type` | VARCHAR(10) | `charge` / `consume` |
| `amount` | INTEGER | 거래 연료량 |
| `reason` | VARCHAR(30) | 거래 사유 |
| `reference_id` | VARCHAR(50) | 관련 리소스 ID (세션 UUID 또는 행성/지역 ID) |
| `balance_after` | INTEGER | 거래 후 잔량 |
| `created_at` | TIMESTAMP | 거래 시각 |

### 무결성 보장

연료 잔량 업데이트와 거래 내역 생성은 **하나의 트랜잭션** 안에서 처리해야 합니다.

```
BEGIN TRANSACTION;
  UPDATE user_fuel SET current_fuel = current_fuel - :amount, ...;
  INSERT INTO fuel_transactions (...) VALUES (...);
COMMIT;
```

잔량이 음수가 되는 것을 방지하기 위해:

```sql
-- CHECK 제약조건
ALTER TABLE user_fuel ADD CONSTRAINT chk_fuel_non_negative CHECK (current_fuel >= 0);
```
