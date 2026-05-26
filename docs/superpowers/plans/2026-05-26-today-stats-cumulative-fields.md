# today-stats 누적 통계 필드 3개 추가 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /api/timer-sessions/today-stats` 응답에 `lifetimeMinutes`, `lifetimeSessionCount`, `monthlyMinutes` 3개 필드를 추가해 클라이언트의 누적 통계(프로필 카드·뱃지 해금)가 정확히 동작하도록 한다.

**Architecture:** 기존 record `TodayStatsResponse`에 비-null Integer 3개를 append-only로 추가한다. Service는 KST 기준 "이번 달" 경계를 계산해 기존 `sumDurationBetween`을 재사용하고, lifetime용 SUM/COUNT는 신규 Repository 메서드 2개로 처리한다. 인덱스(`user_id, started_at DESC`)는 기존 것을 그대로 사용, 마이그레이션·캐싱은 도입하지 않는다.

**Tech Stack:** Spring Boot 4.0.x · Spring Data JPA · JUnit 5 · Mockito · AssertJ · Springdoc(Swagger) · PostgreSQL

**참조 문서:**
- 설계 spec: `docs/superpowers/specs/2026-05-26-today-stats-cumulative-fields-design.md`
- API spec: `docs/api-specs/03_timer.md`
- 코드 컨벤션: 루트 `CLAUDE.md`

**커밋 메시지 컨벤션 (프로젝트 규칙):**
```
today-stats 응답에 누적 통계 필드 3개 추가 : {type} : {설명} #40
```
- `type`: `feat` / `fix` / `refactor` / `test` / `docs` / `chore`
- 이모지·Co-Authored-By 금지

---

## File Structure

| 모듈 | 경로 | 변경 종류 | 역할 |
|------|------|----------|------|
| SS-Study | `study/timer/dto/TodayStatsResponse.java` | Modify | record에 필드 3개 append + `@Schema` 추가 |
| SS-Study | `study/timer/repository/TimerSessionRepository.java` | Modify | `sumDurationByUserId`, `countByUserId` 추가 |
| SS-Study | `study/timer/service/TimerSessionService.java` | Modify | `getTodayStats` 합산 로직 확장 |
| SS-Study | `study/timer/repository/TimerSessionRepositoryTest.java` | Modify | 신규 메서드 통합 테스트 2건 추가 |
| SS-Study | `study/timer/service/TimerSessionServiceTest.java` | Modify | 기존 today-stats 테스트 stub/expected 갱신 + 신규 케이스 3건 추가 |
| SS-Web | `controller/timer/TimerSessionController.java` | Modify | `@ApiResponse examples` JSON 갱신 |
| SS-Web | `controller/timer/TimerSessionControllerTest.java` | Modify | `todayStats_200` 6필드 검증으로 갱신 |
| docs | `docs/api-specs/03_timer.md` | Modify | today-stats 응답 섹션 + 필드 표 갱신 |

DB 마이그레이션 / Entity / 인덱스 변경 **없음**.

---

## Task 1: TodayStatsResponse DTO 확장 (필드 3개 추가)

**목표:** record에 필드를 추가해 컴파일 베이스라인을 확장한다. 이 단계에서는 신규 필드 모두 `0`을 임시로 주입해 빌드를 그린 상태로 유지한다. Repository/Service 로직은 후속 task에서 채운다.

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TodayStatsResponse.java`
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/service/TimerSessionService.java:183`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/service/TimerSessionServiceTest.java` (기존 5개 테스트 컴파일 fix)
- Modify: `SS-Web/src/test/java/com/elipair/spacestudyship/controller/timer/TimerSessionControllerTest.java:238` (생성자 인자 수 fix)

- [ ] **Step 1: `TodayStatsResponse` 필드 3개 추가**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TodayStatsResponse.java` 전체를 아래로 교체:

```java
package com.elipair.spacestudyship.study.timer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "오늘 공부 통계 + 누적 통계 (KST 기준)")
public record TodayStatsResponse(
        @Schema(description = "오늘 총 공부 시간 (분)") Integer totalMinutes,
        @Schema(description = "오늘 완료한 세션 수") Integer sessionCount,
        @Schema(description = "연속 공부 일수 (오늘 포함, KST 기준)") Integer streak,
        @Schema(description = "회원의 전체 누적 공부 시간 (분)") Integer lifetimeMinutes,
        @Schema(description = "회원의 전체 세션 수") Integer lifetimeSessionCount,
        @Schema(description = "이번 달 누적 공부 시간 (분, KST 기준)") Integer monthlyMinutes
) {}
```

- [ ] **Step 2: `TimerSessionService.getTodayStats` 반환문 임시 fix**

`SS-Study/.../service/TimerSessionService.java` 의 `getTodayStats` 마지막 return 문:

기존:
```java
return new TodayStatsResponse(Math.toIntExact(totalMinutes), (int) sessionCount, streak);
```

변경:
```java
// Task 3에서 lifetime/monthly 합산 로직으로 교체 — 현재는 컴파일 유지용 0 주입
return new TodayStatsResponse(Math.toIntExact(totalMinutes), (int) sessionCount, streak, 0, 0, 0);
```

- [ ] **Step 3: 기존 Service 테스트 5개의 생성자 호출 fix**

`SS-Study/src/test/java/.../service/TimerSessionServiceTest.java` 에서 `new TodayStatsResponse(...)`로 비교하는 단 1건 (line 327)만 6필드로 변경:

기존:
```java
assertThat(res).isEqualTo(new TodayStatsResponse(0, 0, 0));
```

변경:
```java
assertThat(res).isEqualTo(new TodayStatsResponse(0, 0, 0, 0, 0, 0));
```

나머지 4건(`todayStats_withData`, `streak_yesterdayLatest`, `streak_brokenChain`, `streak_futureLatest_clampedToToday`)은 개별 필드 `.totalMinutes()/.sessionCount()/.streak()`만 검증하므로 시그니처 변경 영향 없음 — 수정하지 않는다.

- [ ] **Step 4: 기존 Controller 테스트 생성자 호출 fix**

`SS-Web/src/test/java/.../controller/timer/TimerSessionControllerTest.java:238` 의 `todayStats_200`:

기존:
```java
given(service.getTodayStats(1L))
        .willReturn(new TodayStatsResponse(180, 3, 7));
```

변경:
```java
given(service.getTodayStats(1L))
        .willReturn(new TodayStatsResponse(180, 3, 7, 12450, 287, 1820));
```

(jsonPath 검증은 Task 4에서 확장)

- [ ] **Step 5: 빌드/테스트 그린 확인**

Run:
```bash
./gradlew :SS-Study:test :SS-Web:test
```
Expected: PASS (모든 기존 테스트 통과, 컴파일 에러 0)

- [ ] **Step 6: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TodayStatsResponse.java \
        SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/service/TimerSessionService.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/service/TimerSessionServiceTest.java \
        SS-Web/src/test/java/com/elipair/spacestudyship/controller/timer/TimerSessionControllerTest.java
git commit -m "today-stats 응답에 누적 통계 필드 3개 추가 : feat : TodayStatsResponse에 lifetime/monthly 필드 추가 (값은 임시 0) #40"
```

---

## Task 2: Repository 메서드 2개 추가 (TDD)

**목표:** lifetime SUM/COUNT용 메서드 2개를 추가하고 통합 테스트로 검증한다. `monthlyMinutes`는 기존 `sumDurationBetween`을 그대로 재사용하므로 추가하지 않는다.

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepository.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepositoryTest.java`

- [ ] **Step 1: 통합 테스트 2건 작성 (실패 예상)**

`TimerSessionRepositoryTest.java` 의 클래스 끝부분(마지막 `}` 직전)에 아래 두 테스트를 추가:

```java
    @Test
    @DisplayName("sumDurationByUserId: 본인 전체 합산, 다른 user 제외, 빈 결과는 0")
    void sumDurationByUserId() {
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-04-10T01:00:00"), 30, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-01T01:00:00"), 60, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-25T01:00:00"), 90, null, null));
        repository.saveAndFlush(session(2L, LocalDateTime.parse("2026-05-25T01:00:00"), 120, null, null));

        assertThat(repository.sumDurationByUserId(1L)).isEqualTo(180L);
        assertThat(repository.sumDurationByUserId(2L)).isEqualTo(120L);
        assertThat(repository.sumDurationByUserId(999L)).isZero();
    }

    @Test
    @DisplayName("countByUserId: 본인 세션 수, 다른 user 제외, 빈 결과는 0")
    void countByUserId() {
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-23T01:00:00"), 30, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-24T01:00:00"), 30, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-25T01:00:00"), 30, null, null));
        repository.saveAndFlush(session(2L, LocalDateTime.parse("2026-05-25T01:00:00"), 30, null, null));

        assertThat(repository.countByUserId(1L)).isEqualTo(3);
        assertThat(repository.countByUserId(2L)).isEqualTo(1);
        assertThat(repository.countByUserId(999L)).isZero();
    }
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run:
```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.timer.repository.TimerSessionRepositoryTest"
```
Expected: 컴파일 실패 — `sumDurationByUserId(Long)`, `countByUserId(Long)` 미정의

- [ ] **Step 3: Repository에 메서드 2개 추가**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepository.java` 의 `findStartedAtsAfter` 메서드 아래(클래스 닫는 `}` 직전)에 추가:

```java
    // 전체 누적 분: SUM(Integer) → Long, COALESCE로 NULL 방지
    @Query("SELECT COALESCE(SUM(s.durationMinutes), 0L) FROM TimerSession s " +
           "WHERE s.userId = :userId")
    Long sumDurationByUserId(@Param("userId") Long userId);

    // 전체 세션 수: Spring Data 명명 규칙
    long countByUserId(Long userId);
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run:
```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.timer.repository.TimerSessionRepositoryTest"
```
Expected: PASS (신규 2개 + 기존 8개 모두 통과)

- [ ] **Step 5: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepository.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepositoryTest.java
git commit -m "today-stats 응답에 누적 통계 필드 3개 추가 : feat : Repository에 sumDurationByUserId/countByUserId 추가 #40"
```

---

## Task 3: Service 합산 로직 확장 (TDD)

**목표:** `getTodayStats`에 lifetime SUM/COUNT 호출과 KST 기준 이번 달 SUM 호출을 추가해 응답 6필드를 채운다.

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/service/TimerSessionService.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/service/TimerSessionServiceTest.java`

- [ ] **Step 1: 기존 today-stats 테스트 5건에 신규 stub 추가**

신규 Repository 메서드(`sumDurationByUserId`, `countByUserId`) 호출을 추가하면 기존 5개 테스트(`todayStats_empty`, `todayStats_withData`, `streak_yesterdayLatest`, `streak_brokenChain`, `streak_futureLatest_clampedToToday`)에서 lenient mode가 아니라면 stub 누락으로 NullPointerException 발생 가능. **방어적으로 모든 today-stats 관련 테스트에 stub 추가.**

또한 `sumDurationBetween`는 기존에 `any(), any()` 두 호출(오늘 + 이번 달)을 모두 매칭하므로 `0L` 반환 stub만 있으면 충분하지만, `todayStats_withData`처럼 특정 값을 기대하는 경우 `any(), any()`가 두 호출 모두 같은 값을 반환하면 today=monthly가 됨 → 그게 의도된 동작이므로 OK.

각 테스트의 `@BeforeEach` 이후 `given(...)` 블록 직후에 아래 두 줄 추가:

```java
given(sessionRepository.sumDurationByUserId(eq(1L))).willReturn(0L);
given(sessionRepository.countByUserId(eq(1L))).willReturn(0L);
```

`todayStats_empty` (line 317~) 전체를 아래로 교체:

```java
    @Test
    @DisplayName("today-stats: 빈 데이터 → 모두 0")
    void todayStats_empty() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(0L);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        given(sessionRepository.findStartedAtsAfter(eq(1L), any())).willReturn(List.of());
        given(sessionRepository.sumDurationByUserId(eq(1L))).willReturn(0L);
        given(sessionRepository.countByUserId(eq(1L))).willReturn(0L);

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res).isEqualTo(new TodayStatsResponse(0, 0, 0, 0, 0, 0));
    }
```

`todayStats_withData` (line 331~) 전체를 아래로 교체 (lifetime/monthly stub 분리 — `eq` Matcher로 호출 구분):

```java
    @Test
    @DisplayName("today-stats: 정상 데이터 + streak + lifetime/monthly 계산")
    void todayStats_withData() {
        // sumDurationBetween는 오늘 + 이번 달 두 번 호출됨.
        // fixedClock=2026-05-25T12:00:00Z → KST 2026-05-25 21:00 → 오늘=2026-05-25 KST, 월 시작=2026-05-01 KST.
        // 둘 다 any() 매칭 시 마지막 stub이 우선이므로, 명시적으로 호출별 stub을 분리한다.
        given(sessionRepository.sumDurationBetween(eq(1L),
                eq(LocalDateTime.parse("2026-05-24T15:00:00")),    // 오늘 시작 (KST 5/25 00:00 = UTC 5/24 15:00)
                eq(LocalDateTime.parse("2026-05-25T15:00:00"))))   // 내일 시작 (KST 5/26 00:00 = UTC 5/25 15:00)
                .willReturn(180L);
        given(sessionRepository.sumDurationBetween(eq(1L),
                eq(LocalDateTime.parse("2026-04-30T15:00:00")),    // 5월 시작 KST 5/1 00:00 = UTC 4/30 15:00
                eq(LocalDateTime.parse("2026-05-31T15:00:00"))))   // 6월 시작 KST 6/1 00:00 = UTC 5/31 15:00
                .willReturn(1820L);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(3L);
        given(sessionRepository.findStartedAtsAfter(eq(1L), any()))
                .willReturn(List.of(
                        LocalDateTime.parse("2026-05-25T02:00:00"),
                        LocalDateTime.parse("2026-05-23T16:00:00"),
                        LocalDateTime.parse("2026-05-22T16:00:00")
                ));
        given(sessionRepository.sumDurationByUserId(eq(1L))).willReturn(12450L);
        given(sessionRepository.countByUserId(eq(1L))).willReturn(287L);

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res.totalMinutes()).isEqualTo(180);
        assertThat(res.sessionCount()).isEqualTo(3);
        assertThat(res.streak()).isEqualTo(3);
        assertThat(res.lifetimeMinutes()).isEqualTo(12450);
        assertThat(res.lifetimeSessionCount()).isEqualTo(287);
        assertThat(res.monthlyMinutes()).isEqualTo(1820);
    }
```

`streak_yesterdayLatest`, `streak_brokenChain`, `streak_futureLatest_clampedToToday` 3건은 streak만 검증하므로, 각 테스트의 `given(sessionRepository.findStartedAtsAfter...)` 줄 **다음**에 아래 stub 두 줄을 추가:

```java
given(sessionRepository.sumDurationByUserId(eq(1L))).willReturn(0L);
given(sessionRepository.countByUserId(eq(1L))).willReturn(0L);
```

- [ ] **Step 2: 신규 테스트 3건 추가**

`TimerSessionServiceTest.java` 의 `streak_futureLatest_clampedToToday` 메서드 **다음**(클래스 닫는 `}` 직전)에 추가:

```java
    @Test
    @DisplayName("today-stats: lifetime/monthly — KST 월 경계가 sumDurationBetween 인자에 정확히 매핑")
    void todayStats_monthlyBoundary_kst() {
        // fixedClock=2026-05-25T12:00:00Z → KST 5/25.
        // 이번 달 시작 KST 2026-05-01 00:00 = UTC 2026-04-30 15:00
        // 다음 달 시작 KST 2026-06-01 00:00 = UTC 2026-05-31 15:00
        LocalDateTime expectedMonthStartUtc = LocalDateTime.parse("2026-04-30T15:00:00");
        LocalDateTime expectedMonthEndUtc   = LocalDateTime.parse("2026-05-31T15:00:00");

        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(0L);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        given(sessionRepository.findStartedAtsAfter(eq(1L), any())).willReturn(List.of());
        given(sessionRepository.sumDurationByUserId(eq(1L))).willReturn(0L);
        given(sessionRepository.countByUserId(eq(1L))).willReturn(0L);

        service.getTodayStats(1L);

        // sumDurationBetween가 정확히 KST 월 경계(UTC 변환된 값)로 호출됐는지 검증
        verify(sessionRepository).sumDurationBetween(eq(1L),
                eq(expectedMonthStartUtc), eq(expectedMonthEndUtc));
    }

    @Test
    @DisplayName("today-stats: lifetime 0건 → 3 필드 모두 0 (null 금지)")
    void todayStats_lifetimeZero_returnsZeroNotNull() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(0L);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        given(sessionRepository.findStartedAtsAfter(eq(1L), any())).willReturn(List.of());
        given(sessionRepository.sumDurationByUserId(eq(1L))).willReturn(0L);
        given(sessionRepository.countByUserId(eq(1L))).willReturn(0L);

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res.lifetimeMinutes()).isNotNull().isZero();
        assertThat(res.lifetimeSessionCount()).isNotNull().isZero();
        assertThat(res.monthlyMinutes()).isNotNull().isZero();
    }

    @Test
    @DisplayName("today-stats: 지난달 + 이번 달 혼합 → monthly < lifetime, lifetime = 전체 합")
    void todayStats_mixedMonths_lifetimeGreaterThanMonthly() {
        // sumDurationBetween는 (오늘, 이번 달) 두 번 호출 — 호출 인자로 구분
        given(sessionRepository.sumDurationBetween(eq(1L),
                eq(LocalDateTime.parse("2026-05-24T15:00:00")),
                eq(LocalDateTime.parse("2026-05-25T15:00:00"))))
                .willReturn(0L);
        given(sessionRepository.sumDurationBetween(eq(1L),
                eq(LocalDateTime.parse("2026-04-30T15:00:00")),
                eq(LocalDateTime.parse("2026-05-31T15:00:00"))))
                .willReturn(1820L);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        given(sessionRepository.findStartedAtsAfter(eq(1L), any())).willReturn(List.of());
        given(sessionRepository.sumDurationByUserId(eq(1L))).willReturn(12450L);
        given(sessionRepository.countByUserId(eq(1L))).willReturn(287L);

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res.lifetimeMinutes()).isEqualTo(12450);
        assertThat(res.monthlyMinutes()).isEqualTo(1820);
        assertThat(res.monthlyMinutes()).isLessThan(res.lifetimeMinutes());
        assertThat(res.lifetimeSessionCount()).isEqualTo(287);
    }
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run:
```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.timer.service.TimerSessionServiceTest"
```
Expected: FAIL — Service가 아직 lifetime/monthly를 계산하지 않으므로 `lifetimeMinutes/lifetimeSessionCount/monthlyMinutes`가 모두 0으로 반환. `todayStats_withData`, `todayStats_monthlyBoundary_kst`, `todayStats_mixedMonths_lifetimeGreaterThanMonthly` 실패.

- [ ] **Step 4: Service `getTodayStats` 본문 교체**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/service/TimerSessionService.java` 의 `getTodayStats` 메서드(line 165~184) 전체를 아래로 교체:

```java
    public TodayStatsResponse getTodayStats(Long userId) {
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

        // 이번 달 KST 경계 → UTC 변환 후 기존 sumDurationBetween 재사용
        LocalDate monthStartKst = todayKst.withDayOfMonth(1);
        LocalDateTime monthStartUtc = toUtcLdt(monthStartKst.atStartOfDay(ZONE_KST));
        LocalDateTime monthEndUtc   = toUtcLdt(monthStartKst.plusMonths(1).atStartOfDay(ZONE_KST));
        long monthlyMinutes = Optional.ofNullable(
                sessionRepository.sumDurationBetween(userId, monthStartUtc, monthEndUtc))
                .orElse(0L);

        // 전체 누적 (lifetime) — Repository에서 COALESCE로 NULL-safe
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

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run:
```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.timer.service.TimerSessionServiceTest"
```
Expected: PASS (기존 + 신규 모두 통과)

- [ ] **Step 6: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/service/TimerSessionService.java \
        SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/service/TimerSessionServiceTest.java
git commit -m "today-stats 응답에 누적 통계 필드 3개 추가 : feat : Service에 lifetime/monthly 합산 로직 추가 (KST 월 경계) #40"
```

---

## Task 4: Controller MockMvc 테스트 확장 + Swagger 갱신

**목표:** Controller 레벨에서 신규 3필드가 JSON 응답에 직렬화되는지 검증하고, Swagger `examples`를 갱신해 문서가 최신 응답 형태를 반영하게 한다.

**Files:**
- Modify: `SS-Web/src/test/java/com/elipair/spacestudyship/controller/timer/TimerSessionControllerTest.java:236-245`
- Modify: `SS-Web/src/main/java/com/elipair/spacestudyship/controller/timer/TimerSessionController.java:138`

- [ ] **Step 1: MockMvc 테스트에 신규 필드 jsonPath 검증 추가**

`TimerSessionControllerTest.java` 의 `todayStats_200` (line 234~245) 전체를 아래로 교체:

```java
    @Test
    @DisplayName("GET /api/timer-sessions/today-stats — 200, 6필드 (today + lifetime + monthly)")
    void todayStats_200() throws Exception {
        given(service.getTodayStats(1L))
                .willReturn(new TodayStatsResponse(180, 3, 7, 12450, 287, 1820));

        mockMvc.perform(get("/api/timer-sessions/today-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes").value(180))
                .andExpect(jsonPath("$.sessionCount").value(3))
                .andExpect(jsonPath("$.streak").value(7))
                .andExpect(jsonPath("$.lifetimeMinutes").value(12450))
                .andExpect(jsonPath("$.lifetimeSessionCount").value(287))
                .andExpect(jsonPath("$.monthlyMinutes").value(1820));
    }
```

- [ ] **Step 2: 0건 케이스 — null 금지 검증 테스트 추가**

`todayStats_200` 메서드 **다음**에 추가:

```java
    @Test
    @DisplayName("GET /api/timer-sessions/today-stats — 0건 회원: 신규 3필드도 0 (null 아님)")
    void todayStats_zero_neverNull() throws Exception {
        given(service.getTodayStats(1L))
                .willReturn(new TodayStatsResponse(0, 0, 0, 0, 0, 0));

        mockMvc.perform(get("/api/timer-sessions/today-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifetimeMinutes").value(0))
                .andExpect(jsonPath("$.lifetimeSessionCount").value(0))
                .andExpect(jsonPath("$.monthlyMinutes").value(0));
    }
```

- [ ] **Step 3: 테스트 실행 → 통과 확인**

Run:
```bash
./gradlew :SS-Web:test --tests "com.elipair.spacestudyship.controller.timer.TimerSessionControllerTest"
```
Expected: PASS (모든 테스트 그린)

- [ ] **Step 4: Controller Swagger `examples`·`description` 갱신**

`SS-Web/.../controller/timer/TimerSessionController.java` 의 `getTodayStats` 메서드 어노테이션 블록(line 132~140) 전체를 아래로 교체:

기존:
```java
    @Operation(summary = "오늘 공부 통계",
            description = "KST(Asia/Seoul) 기준 오늘의 총 분 / 세션 수 / 연속 일수(streak)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TodayStatsResponse.class),
                            examples = @ExampleObject(value = "{\"totalMinutes\":180,\"sessionCount\":3,\"streak\":7}"))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
```

변경:
```java
    @Operation(summary = "오늘 공부 통계 + 누적 통계",
            description = """
                KST(Asia/Seoul) 기준 통계.

                ### 응답 필드
                - `totalMinutes`, `sessionCount`: 오늘 (KST)
                - `streak`: 연속 공부 일수 (오늘 포함, KST)
                - `lifetimeMinutes`, `lifetimeSessionCount`: 회원의 전체 누적
                - `monthlyMinutes`: 이번 달 누적 (KST 1일 00:00 ~ 다음 달 1일 00:00)

                세션 0건 회원도 6개 필드 모두 `0`을 반환합니다 (null 금지).
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TodayStatsResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "totalMinutes": 180,
                                      "sessionCount": 3,
                                      "streak": 7,
                                      "lifetimeMinutes": 12450,
                                      "lifetimeSessionCount": 287,
                                      "monthlyMinutes": 1820
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
```

- [ ] **Step 5: 컨트롤러 테스트 재실행 (회귀 확인)**

Run:
```bash
./gradlew :SS-Web:test --tests "com.elipair.spacestudyship.controller.timer.TimerSessionControllerTest"
```
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add SS-Web/src/main/java/com/elipair/spacestudyship/controller/timer/TimerSessionController.java \
        SS-Web/src/test/java/com/elipair/spacestudyship/controller/timer/TimerSessionControllerTest.java
git commit -m "today-stats 응답에 누적 통계 필드 3개 추가 : feat : Controller MockMvc 테스트 확장 + Swagger examples 갱신 #40"
```

---

## Task 5: API spec 문서 갱신

**목표:** `docs/api-specs/03_timer.md` 의 today-stats 섹션을 6필드 응답으로 갱신.

**Files:**
- Modify: `docs/api-specs/03_timer.md`

- [ ] **Step 1: today-stats 섹션 위치 확인**

Run:
```bash
grep -n "today-stats" docs/api-specs/03_timer.md
```
Expected: today-stats 헤더가 있는 라인 번호 출력.

- [ ] **Step 2: today-stats 응답 섹션 갱신**

`docs/api-specs/03_timer.md` 에서 today-stats 섹션의 `### Response` 블록(필드 표 + JSON 예시 포함)을 아래로 교체. 정확한 위치는 Step 1에서 확인한 라인 번호 기준.

교체 내용 (응답 본문 블록 전체):

````markdown
### Response

`200 OK`

| 필드 | 타입 | Nullable | 설명 |
|------|------|----------|------|
| `totalMinutes` | Integer | X | 오늘 총 공부 시간 (분, KST) |
| `sessionCount` | Integer | X | 오늘 완료한 세션 수 |
| `streak` | Integer | X | 연속 공부 일수 (오늘 포함, KST 기준) |
| `lifetimeMinutes` | Integer | X | 회원의 전체 누적 공부 시간 (분) |
| `lifetimeSessionCount` | Integer | X | 회원의 전체 세션 수 |
| `monthlyMinutes` | Integer | X | 이번 달 누적 공부 시간 (분, KST 기준) |

> 세션 0건 회원도 6필드 모두 `0`을 반환합니다. `null` 절대 반환하지 않습니다.

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

#### 시간 경계 정의
- **오늘**: `KST 00:00:00` ~ `KST 23:59:59`
- **이번 달**: 이번 달 1일 `KST 00:00:00` ~ 다음 달 1일 `KST 00:00:00` (반열림 `[start, end)`)
- **streak**: 마지막 공부일이 오늘이면 오늘 포함, 어제까지만 했으면 어제 기준. KST 기준 일자 단위 연속.
````

(기존 텍스트와 정확히 매칭되는 큰 블록을 한 번에 교체. 다른 섹션 — 엔드포인트 요약 표 등 — 은 건드리지 않는다.)

- [ ] **Step 3: 마크다운 렌더 확인 (선택)**

문서 형식만 확인. 빌드/테스트 영향 없음.

- [ ] **Step 4: 커밋**

```bash
git add docs/api-specs/03_timer.md
git commit -m "today-stats 응답에 누적 통계 필드 3개 추가 : docs : API spec 03_timer.md 응답 6필드로 갱신 #40"
```

---

## Task 6: 전체 빌드 + 회귀 검증

**목표:** 멀티 모듈 전체 빌드와 테스트가 그린인지 최종 확인.

- [ ] **Step 1: 전체 빌드**

Run:
```bash
./gradlew clean build
```
Expected: `BUILD SUCCESSFUL` — 모든 모듈 컴파일·테스트 그린.

- [ ] **Step 2: 실패 시 회귀 분석**

만약 실패하면 stdout/stderr에서:
- 컴파일 에러: `TodayStatsResponse` 시그니처를 사용하는 추가 위치 누락 가능 → grep으로 점검
  ```bash
  grep -rn "new TodayStatsResponse(" --include="*.java" .
  ```
  발견된 모든 호출이 6-인자 형태인지 확인.
- 테스트 실패: Mockito stub 누락 → Task 3 Step 1 가이드 다시 검토.

Step 1·2를 반복해서 그린 상태로 만든다.

- [ ] **Step 3: 최종 git status 확인 (작업 트리 클린)**

Run:
```bash
git status
```
Expected: `nothing to commit, working tree clean`. 미커밋 변경 없음.

- [ ] **Step 4: 변경 요약 확인**

Run:
```bash
git log --oneline origin/main..HEAD
```
Expected: 5개 커밋 (Task 1~5)이 표시됨. 메시지는 모두 `today-stats 응답에 누적 통계 필드 3개 추가 : {type} : ... #40` 형태.

---

## 완료 조건 (Definition of Done)

- [ ] `TodayStatsResponse` record가 6필드(Integer, 모두 비-null)로 확장됨.
- [ ] Repository에 `sumDurationByUserId`, `countByUserId` 메서드 2개 추가.
- [ ] Service `getTodayStats`가 lifetime/monthly 합산 호출을 포함하고, KST 월 경계가 정확히 UTC로 변환됨.
- [ ] Controller Swagger `examples`·`description`이 신규 필드 포함 형태로 갱신.
- [ ] `docs/api-specs/03_timer.md` 갱신.
- [ ] 신규/기존 테스트 모두 통과: Repository(10건), Service(20+건), Controller(12+건).
- [ ] `./gradlew clean build` 그린.
- [ ] DB 마이그레이션·인덱스·Entity 변경 0건 (호환성 보장).
