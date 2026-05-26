# today-stats 응답에 누적 통계 필드 3개 추가 (Design Spec)

- **이슈**: #40
- **브랜치**: `20260526_#40_today_stats_응답에_누적_통계_필드_3개_추가`
- **작성일**: 2026-05-26
- **종류**: API 확장 (호환성 유지)

---

## 1. 배경 / 문제 정의

### 1.1 현재 동작
클라이언트의 누적 통계 Provider 3종이 `GET /api/timer-sessions`의 **첫 페이지(20개)만** 합산해 통계를 계산하고 있다.

- `totalStudyMinutesProvider`
- `totalSessionCountProvider`
- `monthlyStudyMinutesProvider`

### 1.2 영향
| 영역 | 증상 |
|------|------|
| 프로필 화면 | "공부 시간" 통계 카드가 최근 20세션 기준으로만 표시 — 실제 누적과 다름 |
| 뱃지 시스템 | "총 100시간 공부", "총 50회 세션 완료" 류 조건 평가가 부정확 → 영원히 해금되지 않을 가능성 |
| API 트래픽 | 클라가 모든 페이지 순회 시 N번 API 호출 — 세션이 많아질수록 비효율 |

### 1.3 해결 방향
새 엔드포인트를 만드는 대신 **기존 `GET /api/timer-sessions/today-stats` 응답을 확장**한다.

선택 사유:
- 클라의 호출 시점·캐시 정책이 today-stats와 동일 (홈/프로필 진입 시).
- API 표면적 최소화.
- 기존 `TodayStatsResponse` 스키마와 자연스러운 확장 관계.

---

## 2. 변경 범위

### 2.1 API 계약 (불변/추가)
- 엔드포인트, 메서드, 인증, query/header 모두 **불변**.
- 응답에 필드 3개 **추가** (기존 필드 순서·이름 불변).

### 2.2 응답 스키마 — `TodayStatsResponse`

| 필드 | 타입 | 기존/신규 | 의미 |
|------|------|-----------|------|
| `totalMinutes` | Integer | 기존 | 오늘 총 공부 시간 (분, KST) |
| `sessionCount` | Integer | 기존 | 오늘 완료한 세션 수 (KST) |
| `streak` | Integer | 기존 | 연속 공부 일수 (오늘 포함, KST) |
| `lifetimeMinutes` | Integer | **신규** | 회원의 전체 누적 공부 시간 (분) |
| `lifetimeSessionCount` | Integer | **신규** | 회원의 전체 세션 수 |
| `monthlyMinutes` | Integer | **신규** | 이번 달 누적 공부 시간 (분, KST 기준) |

#### 응답 예시
```json
{
  "totalMinutes": 180,
  "sessionCount": 3,
  "streak": 7,
  "lifetimeMinutes": 12450,
  "lifetimeSessionCount": 287,
  "monthlyMinutes": 1820
}
```

### 2.3 0건 케이스
- 세션이 0건인 회원은 신규 3필드 모두 `0` 반환.
- **`null` 금지** (DTO/스키마 모두 비-null 정수).
- DB 단에서는 `COALESCE(SUM(...), 0L)`로 NULL 방지.

---

## 3. 시간 경계 정의

### 3.1 "이번 달" 경계 (KST)
- streak 계산과 **동일한 타임존(Asia/Seoul) 기준**.
- 시작: 이번 달 1일 00:00 KST
- 종료(exclusive): 다음 달 1일 00:00 KST

### 3.2 산정식 (의사코드)
```
todayKst       = LocalDate.now(clock, Asia/Seoul)
monthStartKst  = todayKst.withDayOfMonth(1)
monthEndKst    = monthStartKst.plusMonths(1)
monthStartUtc  = monthStartKst.atStartOfDay(Asia/Seoul) → UTC LocalDateTime
monthEndUtc    = monthEndKst.atStartOfDay(Asia/Seoul)   → UTC LocalDateTime

monthlyMinutes = SUM(duration_minutes)
                  WHERE user_id = ?
                    AND started_at >= monthStartUtc
                    AND started_at <  monthEndUtc
```

### 3.3 KST 월 경계 예시
- UTC `2026-04-30 16:00:00` = KST `2026-05-01 01:00:00` → **5월**에 집계.
- UTC `2026-04-30 14:59:00` = KST `2026-04-30 23:59:00` → **4월**에 집계.

---

## 4. 구현 설계

### 4.1 영향 모듈
| 모듈 | 파일 |
|------|------|
| SS-Study | `dto/TodayStatsResponse.java` (필드 추가) |
| SS-Study | `repository/TimerSessionRepository.java` (메서드 2개 추가) |
| SS-Study | `service/TimerSessionService.java` (`getTodayStats` 합산 로직 확장) |
| SS-Web | `controller/timer/TimerSessionController.java` (Swagger `examples` 갱신) |
| docs | `docs/api-specs/03_timer.md` (today-stats 응답 섹션 갱신) |
| docs | `docs/api-docs.json` (수동 관리 시 동기화 — 빌드 자동 생성이면 생략) |

### 4.2 Repository 변경
```java
// 추가: 전체 누적 분 (COALESCE로 NULL 방지)
@Query("SELECT COALESCE(SUM(s.durationMinutes), 0L) FROM TimerSession s " +
       "WHERE s.userId = :userId")
Long sumDurationByUserId(@Param("userId") Long userId);

// 추가: 전체 세션 수 (Spring Data 메서드 명명 규칙)
long countByUserId(Long userId);
```
- `monthlyMinutes`는 **기존 `sumDurationBetween(userId, start, end)` 재사용** — 신규 메서드 X.
- 인덱스 `idx_timer_sessions_user_started (user_id, started_at DESC)` 가 기존에 존재 → 신규 두 쿼리도 동일 인덱스로 커버.

### 4.3 Service 변경 — `getTodayStats`

```java
public TodayStatsResponse getTodayStats(Long userId) {
    // ── 기존 로직 (today + streak) ─────────────────────────
    LocalDate todayKst = LocalDate.now(clock.withZone(ZONE_KST));
    LocalDateTime todayStartUtc    = toUtcLdt(todayKst.atStartOfDay(ZONE_KST));
    LocalDateTime tomorrowStartUtc = toUtcLdt(todayKst.plusDays(1).atStartOfDay(ZONE_KST));

    long totalMinutes = Optional.ofNullable(
            sessionRepository.sumDurationBetween(userId, todayStartUtc, tomorrowStartUtc))
            .orElse(0L);
    long sessionCount = sessionRepository
            .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                    userId, todayStartUtc, tomorrowStartUtc);

    LocalDateTime lookbackStart = toUtcLdt(
            todayKst.minusDays(STREAK_LOOKBACK_DAYS).atStartOfDay(ZONE_KST));
    List<LocalDateTime> startedAts = sessionRepository
            .findStartedAtsAfter(userId, lookbackStart);
    int streak = computeStreak(startedAts, todayKst);

    // ── 신규: 이번 달 (KST) ────────────────────────────────
    LocalDate monthStartKst = todayKst.withDayOfMonth(1);
    LocalDateTime monthStartUtc = toUtcLdt(monthStartKst.atStartOfDay(ZONE_KST));
    LocalDateTime monthEndUtc   = toUtcLdt(monthStartKst.plusMonths(1).atStartOfDay(ZONE_KST));
    long monthlyMinutes = Optional.ofNullable(
            sessionRepository.sumDurationBetween(userId, monthStartUtc, monthEndUtc))
            .orElse(0L);

    // ── 신규: 전체 누적 ────────────────────────────────────
    long lifetimeMinutes      = Optional.ofNullable(
            sessionRepository.sumDurationByUserId(userId)).orElse(0L);
    long lifetimeSessionCount = sessionRepository.countByUserId(userId);

    return new TodayStatsResponse(
            Math.toIntExact(totalMinutes),
            (int) sessionCount,
            streak,
            Math.toIntExact(lifetimeMinutes),
            Math.toIntExact(lifetimeSessionCount),
            Math.toIntExact(monthlyMinutes)
    );
}
```

### 4.4 쿼리 개수
| 단계 | 쿼리 수 |
|------|---------|
| 기존 (today + streak) | 3 |
| 신규 (lifetime SUM + lifetime COUNT + monthly SUM) | +3 |
| **합계** | **6** |

모두 동일 인덱스 + 단일 사용자 한정 범위 스캔이라 ms 단위 영향. 캐싱/비정규화는 도입하지 않음 (YAGNI).

### 4.5 오버플로우 안전
- `Math.toIntExact(Long → int)` 적용 — overflow 발생 시 즉시 `ArithmeticException`.
- 분 단위 누적은 `1440 × 365 × 10년 ≈ 5.25M`로 int 안전 범위 내.

---

## 5. 호환성 / 마이그레이션

| 항목 | 영향 |
|------|------|
| DB 스키마 | 변경 없음 (마이그레이션 파일 추가 X) |
| 인덱스 | 변경 없음 |
| 기존 클라 | 신규 필드는 무시 → **무중단** |
| 신규 클라 | 누적값 즉시 사용 가능 |
| 롤백 | DTO/Service만 되돌리면 됨 (DB 영향 0) |

---

## 6. 테스트 계획

### 6.1 Service 단위 테스트 (`TimerSessionServiceTest`)
| 케이스 | 기대값 |
|--------|--------|
| 세션 0건 | `lifetimeMinutes=0`, `lifetimeSessionCount=0`, `monthlyMinutes=0` |
| 오늘만 1건 (90분) | `total=90`, `lifetime=90`, `monthly=90`, `lifetimeCount=1` |
| 지난달 + 이번 달 혼합 | `monthly < lifetime`, `lifetime = SUM(전체)` |
| KST 월 경계: UTC 4/30 16:00 세션 | KST 5/1 01:00 → 5월 `monthlyMinutes`에 포함 |
| 큰 누적 (수십 시간) | 정수 반환, 음수/NULL 없음 |

### 6.2 Controller 통합 테스트 (`TimerSessionControllerTest` MockMvc)
- `GET /api/timer-sessions/today-stats` 응답 JSON에 **신규 필드 3개 존재** 검증.
- 응답 200 + 필드 타입 정수 검증.
- 인증 누락 시 401 (기존 동작 회귀 없음).

### 6.3 회귀 테스트
- 기존 today/streak 테스트가 모두 통과해야 함 (수정 없이).

---

## 7. 문서 갱신

### 7.1 `docs/api-specs/03_timer.md`
- `today-stats` 섹션의 응답 예시·필드 표에 신규 3필드 추가.
- 시간 경계(KST) 명시.

### 7.2 `docs/api-docs.json`
- springdoc이 빌드 시 자동 생성하는 산출물이면 별도 수정 불필요.
- 수동으로 PR에 포함시키는 정책이면 빌드 후 산출물 동기화.
- (확인 후 plan 단계에서 결정)

### 7.3 Swagger 어노테이션
- `TodayStatsResponse` 각 필드에 `@Schema(description=...)` 추가.
- Controller `@ApiResponse` `examples`를 신규 필드 포함 형태로 교체.

---

## 8. 비결정/추후 검토 (Out of Scope)
- members 테이블에 누적 통계 비정규화 (현재 규모에서 불필요).
- Redis TTL 캐싱.
- 주간/연간 통계 (요청 범위 외).
- 클라 측 Provider 로직 수정 (백엔드 PR 외 작업).

---

## 9. 완료 조건 (Definition of Done)
- [ ] `TodayStatsResponse` 6필드 record로 확장 + `@Schema` 적용.
- [ ] Repository 메서드 2개 추가 (`sumDurationByUserId`, `countByUserId`).
- [ ] Service `getTodayStats` 합산 로직 확장.
- [ ] Controller Swagger `examples` 갱신.
- [ ] Service/Controller 테스트 6.1·6.2 케이스 추가, 전 테스트 그린.
- [ ] `docs/api-specs/03_timer.md` 갱신.
- [ ] 빌드 그린 (`./gradlew build`).
