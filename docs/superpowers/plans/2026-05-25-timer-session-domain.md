# Timer Session Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the 3 endpoints of `/api/timer-sessions` (POST 저장, GET 목록, GET today-stats) with single-transaction integration to Fuel charging and Todo `actualMinutes` accumulation.

**Architecture:** New `study/timer/` package in SS-Study mirrors the Fuel domain structure. `TimerSessionService` orchestrates session save → `FuelService.charge()` → `TodoService.addActualMinutes()` in one transaction, using `sessionId` as the Fuel `transactionId` for natural idempotency. Optional `Idempotency-Key` HTTP header provides a second layer of dedup via a partial unique index on `(user_id, idempotency_key)`. Streak computation runs in-memory over the last 365 days, with day boundaries in `Asia/Seoul` while DB stays UTC.

**Tech Stack:** Spring Boot 4.0.2, Java 21, Gradle multi-module (`SS-Study`, `SS-Web`, `SS-Common`), JPA + Hibernate, PostgreSQL 16 (Testcontainers in tests), Flyway, JUnit 5 + Mockito + AssertJ, springdoc-openapi (Swagger), Lombok.

**Spec:** [docs/superpowers/specs/2026-05-25-timer-session-domain-design.md](../specs/2026-05-25-timer-session-domain-design.md)
**Issue:** #25
**Branch:** `20260422_#25_타이머_세션_도메인_구현`
**Commit message format:** `타이머 세션 도메인 구현 : {type} : {설명} #25`

---

## File Map

### Create
| Path | Responsibility |
|------|----------------|
| `SS-Web/src/main/resources/db/migration/V0_0_39__add_timer_sessions.sql` | DDL for `timer_sessions` + indexes + partial unique on idempotency_key |
| `SS-Web/src/main/java/com/elipair/spacestudyship/config/BeanConfig.java` | Spring `Clock` bean (`Clock.systemUTC()`) |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/entity/TimerSession.java` | JPA entity with `@Check` constraints |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepository.java` | JPA repository with filter, sum, count, distinct-day queries |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionCreateRequest.java` | POST 요청 body |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionResponse.java` | 세션 단건 응답 record |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionCreateResponse.java` | POST 응답 `{ session, fuelCharged }` |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionListResponse.java` | 목록 + 페이지 envelope |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TodayStatsResponse.java` | 오늘 통계 응답 |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/service/TimerSessionService.java` | 검증 + 저장 + Fuel/Todo 통합 + 통계 + streak |
| `SS-Web/src/main/java/com/elipair/spacestudyship/controller/timer/TimerSessionController.java` | 3개 엔드포인트, Swagger 풀세트 |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/entity/TimerSessionTest.java` | Entity static factory 동작 |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepositoryTest.java` | Repository 쿼리 + 부분 unique 인덱스 |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/service/TimerSessionServiceTest.java` | 검증 5케이스 + create + list + today-stats + streak |
| `SS-Web/src/test/java/com/elipair/spacestudyship/controller/timer/TimerSessionControllerTest.java` | MockMvc 정상/에러 경로 |

### Modify
| Path | Change |
|------|--------|
| `version.yml` | `version: "0.0.38"` → `"0.0.39"`, `version_code: 38` → `39`, `last_updated` |
| `SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ErrorCode.java` | 5개 enum 추가 |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/repository/TodoRepository.java` | `@Modifying @Query addActualMinutes` 추가 |
| `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java` | `addActualMinutes(userId, todoId, minutes)` 메서드 추가 |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/StudyTestApplication.java` | `@EnableJpaRepositories` 패키지에 `study.timer.repository` 추가 |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java` | `addActualMinutes` 회귀 테스트 추가 |
| `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoRepositoryTest.java` | `addActualMinutes` 쿼리 회귀 테스트 추가 |
| `CLAUDE.md` | 마이그레이션 이력 표에 0.0.39 추가 |

---

## Task 1: 사전 작업 (chore)

version bump, Flyway 마이그레이션, ErrorCode 추가, Clock 빈 등록.

**Files:**
- Modify: `version.yml`
- Create: `SS-Web/src/main/resources/db/migration/V0_0_39__add_timer_sessions.sql`
- Modify: `SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ErrorCode.java`
- Create: `SS-Web/src/main/java/com/elipair/spacestudyship/config/BeanConfig.java`

### Steps

- [ ] **Step 1.1: version.yml bump (0.0.38 → 0.0.39)**

`version.yml` 의 다음 두 줄과 `last_updated`를 변경:
```yaml
version: "0.0.39"
version_code: 39
```
`metadata.last_updated`는 오늘 날짜 + 시각으로 갱신 (`2026-05-25 HH:MM:SS`).

- [ ] **Step 1.2: V0_0_39 마이그레이션 SQL 작성**

`SS-Web/src/main/resources/db/migration/V0_0_39__add_timer_sessions.sql`:
```sql
-- timer_sessions: 공부 타이머 세션 기록
CREATE TABLE IF NOT EXISTS timer_sessions (
    id                VARCHAR(36)  PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    todo_id           VARCHAR(36),
    todo_title        VARCHAR(100),
    started_at        TIMESTAMP    NOT NULL,
    ended_at          TIMESTAMP    NOT NULL,
    duration_minutes  INTEGER      NOT NULL,
    idempotency_key   VARCHAR(80),
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT fk_timer_sessions_member FOREIGN KEY (user_id)
        REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT chk_timer_duration_positive CHECK (duration_minutes > 0),
    CONSTRAINT chk_timer_duration_max      CHECK (duration_minutes <= 1440),
    CONSTRAINT chk_timer_time_order        CHECK (ended_at > started_at)
);

CREATE INDEX IF NOT EXISTS idx_timer_sessions_user_started
    ON timer_sessions (user_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_timer_sessions_user_todo
    ON timer_sessions (user_id, todo_id);

-- Idempotency: 동일 (user, key) 중복 INSERT 방지. key=NULL은 다중 허용 (부분 unique)
CREATE UNIQUE INDEX IF NOT EXISTS uq_timer_sessions_user_idem
    ON timer_sessions (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
```

- [ ] **Step 1.3: ErrorCode 5개 추가**

`SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ErrorCode.java` 의 `// Fuel` 블록과 `// Common` 블록 **사이**에 다음을 삽입:
```java
    // Timer
    INVALID_SESSION_TIME(HttpStatus.BAD_REQUEST, "시작 시각이 종료 시각보다 늦거나 같습니다."),
    INVALID_DURATION(HttpStatus.BAD_REQUEST, "공부 시간이 시작/종료 시각 간격보다 큽니다."),
    SESSION_TOO_SHORT(HttpStatus.BAD_REQUEST, "공부 시간은 1분 이상이어야 합니다."),
    SESSION_TOO_LONG(HttpStatus.BAD_REQUEST, "공부 시간은 24시간(1440분)을 초과할 수 없습니다."),
    FUTURE_SESSION(HttpStatus.BAD_REQUEST, "미래 시각의 세션은 저장할 수 없습니다."),
```

- [ ] **Step 1.4: Clock 빈 등록**

`SS-Web/src/main/java/com/elipair/spacestudyship/config/BeanConfig.java` (신규):
```java
package com.elipair.spacestudyship.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BeanConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 1.5: 컴파일 확인**

Run:
```bash
./gradlew :SS-Common:compileJava :SS-Web:compileJava
```
Expected: BUILD SUCCESSFUL. (테스트 미실행)

- [ ] **Step 1.6: 커밋**

```bash
git add version.yml \
  SS-Web/src/main/resources/db/migration/V0_0_39__add_timer_sessions.sql \
  SS-Common/src/main/java/com/elipair/spacestudyship/common/exception/ErrorCode.java \
  SS-Web/src/main/java/com/elipair/spacestudyship/config/BeanConfig.java
git commit -m "타이머 세션 도메인 구현 : chore : 사전 작업 (version 0.0.39, V0_0_39 마이그레이션, ErrorCode 5개, Clock 빈) #25"
```

---

## Task 2: TimerSession Entity + Repository + 통합 테스트

Entity, Repository, Repository 통합 테스트를 TDD로 작성.

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/entity/TimerSession.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepository.java`
- Create: `SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/entity/TimerSessionTest.java`
- Create: `SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepositoryTest.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/StudyTestApplication.java`

### Steps

- [ ] **Step 2.1: TimerSession Entity static factory 테스트 작성**

`SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/entity/TimerSessionTest.java`:
```java
package com.elipair.spacestudyship.study.timer.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimerSessionTest {

    @Test
    @DisplayName("of: 모든 필드 세팅된 인스턴스 생성")
    void of_setsAllFields() {
        LocalDateTime start = LocalDateTime.parse("2026-05-25T01:00:00");
        LocalDateTime end   = LocalDateTime.parse("2026-05-25T02:30:00");

        TimerSession s = TimerSession.of(
                "sess-1", 1L, "todo-1", "수학",
                start, end, 90, "idem-1");

        assertThat(s.getId()).isEqualTo("sess-1");
        assertThat(s.getUserId()).isEqualTo(1L);
        assertThat(s.getTodoId()).isEqualTo("todo-1");
        assertThat(s.getTodoTitle()).isEqualTo("수학");
        assertThat(s.getStartedAt()).isEqualTo(start);
        assertThat(s.getEndedAt()).isEqualTo(end);
        assertThat(s.getDurationMinutes()).isEqualTo(90);
        assertThat(s.getIdempotencyKey()).isEqualTo("idem-1");
    }

    @Test
    @DisplayName("of: nullable 필드 (todoId, todoTitle, idempotencyKey) null 허용")
    void of_allowsNullables() {
        TimerSession s = TimerSession.of(
                "sess-2", 1L, null, null,
                LocalDateTime.parse("2026-05-25T01:00:00"),
                LocalDateTime.parse("2026-05-25T02:00:00"),
                60, null);

        assertThat(s.getTodoId()).isNull();
        assertThat(s.getTodoTitle()).isNull();
        assertThat(s.getIdempotencyKey()).isNull();
    }
}
```

- [ ] **Step 2.2: 테스트 실행 — 컴파일 실패 확인**

Run:
```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.timer.entity.TimerSessionTest"
```
Expected: COMPILATION FAILED (TimerSession 클래스 없음).

- [ ] **Step 2.3: TimerSession Entity 구현**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/entity/TimerSession.java`:
```java
package com.elipair.spacestudyship.study.timer.entity;

import com.elipair.spacestudyship.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Checks;

import java.time.LocalDateTime;

/**
 * 공부 타이머 세션 기록.
 *
 * 시간 필드(startedAt/endedAt)는 모두 UTC로 해석한다.
 * 서비스 진입 시점에 Instant → LocalDateTime UTC 변환을 거친다.
 *
 * id는 서버 생성 UUID이며, Fuel 충전 시 transactionId로 재사용되어
 * 충전 idempotency를 보장한다.
 */
@Entity
@Checks({
        @Check(name = "chk_timer_duration_positive", constraints = "duration_minutes > 0"),
        @Check(name = "chk_timer_duration_max",      constraints = "duration_minutes <= 1440"),
        @Check(name = "chk_timer_time_order",        constraints = "ended_at > started_at")
})
@Table(name = "timer_sessions",
        indexes = {
                @Index(name = "idx_timer_sessions_user_started", columnList = "user_id, started_at DESC"),
                @Index(name = "idx_timer_sessions_user_todo", columnList = "user_id, todo_id")
        })
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimerSession extends BaseTimeEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "todo_id", length = 36)
    private String todoId;

    @Column(name = "todo_title", length = 100)
    private String todoTitle;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at", nullable = false)
    private LocalDateTime endedAt;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    public static TimerSession of(String id, Long userId, String todoId, String todoTitle,
                                  LocalDateTime startedAt, LocalDateTime endedAt,
                                  int durationMinutes, String idempotencyKey) {
        return TimerSession.builder()
                .id(id).userId(userId).todoId(todoId).todoTitle(todoTitle)
                .startedAt(startedAt).endedAt(endedAt)
                .durationMinutes(durationMinutes)
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
```

- [ ] **Step 2.4: 테스트 실행 — 통과 확인**

Run:
```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.timer.entity.TimerSessionTest"
```
Expected: 2 tests passed.

- [ ] **Step 2.5: StudyTestApplication에 timer.repository 등록**

`SS-Study/src/test/java/com/elipair/spacestudyship/study/StudyTestApplication.java` 의 `@EnableJpaRepositories`:
```java
@EnableJpaRepositories(basePackages = {
        "com.elipair.spacestudyship.study.todo.repository",
        "com.elipair.spacestudyship.study.fuel.repository",
        "com.elipair.spacestudyship.study.timer.repository"
})
```

- [ ] **Step 2.6: TimerSessionRepository 테스트 작성**

`SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepositoryTest.java`:
```java
package com.elipair.spacestudyship.study.timer.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.timer.entity.TimerSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = StudyTestApplication.class)
@Transactional
class TimerSessionRepositoryTest {

    @Autowired
    TimerSessionRepository repository;

    private TimerSession session(Long userId, LocalDateTime startedAt, int duration, String todoId, String idem) {
        return TimerSession.of(
                UUID.randomUUID().toString(), userId, todoId, todoId == null ? null : "title",
                startedAt, startedAt.plusMinutes(duration), duration, idem);
    }

    @Test
    @DisplayName("findByUserIdAndIdempotencyKey: 동일 키 조회 시 반환")
    void findByIdempotencyKey() {
        TimerSession saved = repository.saveAndFlush(
                session(1L, LocalDateTime.parse("2026-05-25T01:00:00"), 30, null, "idem-1"));

        assertThat(repository.findByUserIdAndIdempotencyKey(1L, "idem-1"))
                .isPresent()
                .get().extracting(TimerSession::getId).isEqualTo(saved.getId());
        assertThat(repository.findByUserIdAndIdempotencyKey(1L, "nope")).isEmpty();
        assertThat(repository.findByUserIdAndIdempotencyKey(2L, "idem-1")).isEmpty();
    }

    @Test
    @DisplayName("partial unique index: 동일 (user, key) 중복 INSERT 실패, key=NULL은 다중 허용")
    void partialUniqueIndex() {
        repository.saveAndFlush(
                session(1L, LocalDateTime.parse("2026-05-25T01:00:00"), 30, null, "idem-1"));

        // 동일 (user, key) 두번째 INSERT는 실패
        assertThatThrownBy(() -> repository.saveAndFlush(
                session(1L, LocalDateTime.parse("2026-05-25T02:00:00"), 30, null, "idem-1")))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 다른 user는 같은 key 허용
        repository.saveAndFlush(
                session(2L, LocalDateTime.parse("2026-05-25T01:00:00"), 30, null, "idem-1"));

        // 같은 user지만 key=NULL은 다중 허용
        repository.saveAndFlush(
                session(1L, LocalDateTime.parse("2026-05-25T03:00:00"), 30, null, null));
        repository.saveAndFlush(
                session(1L, LocalDateTime.parse("2026-05-25T04:00:00"), 30, null, null));
    }

    @Test
    @DisplayName("findByFilters: userId만 — 본인 세션 페이지네이션")
    void findByFilters_userIdOnly() {
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-25T01:00:00"), 30, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-25T02:00:00"), 30, null, null));
        repository.saveAndFlush(session(2L, LocalDateTime.parse("2026-05-25T01:00:00"), 30, null, null));

        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<TimerSession> page = repository.findByFilters(1L, null, null, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getStartedAt())
                .isEqualTo(LocalDateTime.parse("2026-05-25T02:00:00"));  // DESC sort
    }

    @Test
    @DisplayName("findByFilters: 날짜 범위 [start, end) 반열림")
    void findByFilters_dateRange() {
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-23T15:00:00"), 30, null, null));  // 한국 5/24
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-24T15:00:00"), 30, null, null));  // 한국 5/25
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-25T15:00:00"), 30, null, null));  // 한국 5/26

        Page<TimerSession> page = repository.findByFilters(
                1L,
                LocalDateTime.parse("2026-05-24T00:00:00"),
                LocalDateTime.parse("2026-05-25T00:00:00"),
                null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt")));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStartedAt())
                .isEqualTo(LocalDateTime.parse("2026-05-24T15:00:00"));
    }

    @Test
    @DisplayName("findByFilters: todoId 필터")
    void findByFilters_todoId() {
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-25T01:00:00"), 30, "todo-A", null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-25T02:00:00"), 30, "todo-B", null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-25T03:00:00"), 30, null, null));

        Page<TimerSession> page = repository.findByFilters(
                1L, null, null, "todo-A",
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startedAt")));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTodoId()).isEqualTo("todo-A");
    }

    @Test
    @DisplayName("sumDurationBetween: 빈 결과 → 0, 정상 → 합산")
    void sumDurationBetween() {
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-24T01:00:00"), 30, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-24T03:00:00"), 60, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-23T01:00:00"), 90, null, null));

        Integer sum = repository.sumDurationBetween(1L,
                LocalDateTime.parse("2026-05-24T00:00:00"),
                LocalDateTime.parse("2026-05-25T00:00:00"));
        assertThat(sum).isEqualTo(90);

        Integer none = repository.sumDurationBetween(1L,
                LocalDateTime.parse("2026-06-01T00:00:00"),
                LocalDateTime.parse("2026-06-02T00:00:00"));
        assertThat(none).isZero();
    }

    @Test
    @DisplayName("count: 날짜 범위 안 세션 수")
    void countBetween() {
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-24T01:00:00"), 30, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-24T03:00:00"), 60, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-23T01:00:00"), 90, null, null));

        long n = repository.countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                1L,
                LocalDateTime.parse("2026-05-24T00:00:00"),
                LocalDateTime.parse("2026-05-25T00:00:00"));
        assertThat(n).isEqualTo(2);
    }

    @Test
    @DisplayName("findStartedAtsAfter: 본인 + start 이후만")
    void findStartedAtsAfter() {
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-24T01:00:00"), 30, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-25T01:00:00"), 30, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-23T01:00:00"), 30, null, null));
        repository.saveAndFlush(session(2L, LocalDateTime.parse("2026-05-25T01:00:00"), 30, null, null));

        List<LocalDateTime> dates = repository.findStartedAtsAfter(
                1L, LocalDateTime.parse("2026-05-24T00:00:00"));

        assertThat(dates).hasSize(2);
    }
}
```

- [ ] **Step 2.7: 테스트 실행 — 컴파일 실패 확인**

Run:
```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.timer.repository.TimerSessionRepositoryTest"
```
Expected: COMPILATION FAILED (TimerSessionRepository 클래스 없음).

- [ ] **Step 2.8: TimerSessionRepository 구현**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepository.java`:
```java
package com.elipair.spacestudyship.study.timer.repository;

import com.elipair.spacestudyship.study.timer.entity.TimerSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TimerSessionRepository extends JpaRepository<TimerSession, String> {

    Optional<TimerSession> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    @Query("""
        SELECT s FROM TimerSession s
        WHERE s.userId = :userId
          AND (:start  IS NULL OR s.startedAt >= :start)
          AND (:end    IS NULL OR s.startedAt <  :end)
          AND (:todoId IS NULL OR s.todoId = :todoId)
        """)
    Page<TimerSession> findByFilters(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("todoId") String todoId,
            Pageable pageable);

    @Query("SELECT COALESCE(SUM(s.durationMinutes), 0) FROM TimerSession s " +
           "WHERE s.userId = :userId AND s.startedAt >= :start AND s.startedAt < :end")
    Integer sumDurationBetween(@Param("userId") Long userId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end);

    long countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long userId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT s.startedAt FROM TimerSession s " +
           "WHERE s.userId = :userId AND s.startedAt >= :start")
    List<LocalDateTime> findStartedAtsAfter(@Param("userId") Long userId,
                                            @Param("start") LocalDateTime start);
}
```

- [ ] **Step 2.9: 테스트 실행 — 통과 확인**

Run:
```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.timer.repository.TimerSessionRepositoryTest"
```
Expected: 8 tests passed (테스트 시작 시 Testcontainers PostgreSQL 컨테이너 기동 — 30~60초 소요).

- [ ] **Step 2.10: 커밋**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/entity/TimerSession.java \
  SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepository.java \
  SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/entity/TimerSessionTest.java \
  SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/repository/TimerSessionRepositoryTest.java \
  SS-Study/src/test/java/com/elipair/spacestudyship/study/StudyTestApplication.java
git commit -m "타이머 세션 도메인 구현 : feat : TimerSession Entity와 Repository 구현 (통합 테스트 포함) #25"
```

---

## Task 3: DTOs + TimerSessionService + 단위 테스트

5개 DTO record와 핵심 서비스 로직(검증/저장/목록/통계/streak)을 TDD로 작성. **이 Task에서 TimerSessionService가 의존하는 `TodoService.addActualMinutes`는 다음 Task 4에서 추가되므로, 본 Task의 Service는 컴파일을 위해 Task 4를 먼저 끝내거나 (대안) Task 4를 끼워 진행한다. 본 계획은 DTO와 Service test/impl을 먼저 작성하되, `todoService.addActualMinutes(...)` 호출은 implementation에 포함시키고 컴파일은 Task 4 완료 후에 보장한다.**

이를 단순화하기 위해, **Task 3와 Task 4를 같은 PR/커밋 흐름의 두 단계로 진행**한다. 본 Task 끝에서 Service까지만 구현하고, 첫 컴파일 통과는 Task 4의 TodoService 메서드 추가 후 보장.

> 실용적 진행 가이드: Service 작성 직전에 Task 4의 Step 4.1~4.6 (TodoRepository/Service 변경)을 먼저 처리한 뒤 Service를 작성하면 컴파일 막힘 없음. 작업자 재량.

**Files:**
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionCreateRequest.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionResponse.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionCreateResponse.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionListResponse.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TodayStatsResponse.java`
- Create: `SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/service/TimerSessionService.java`
- Create: `SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/service/TimerSessionServiceTest.java`

### Steps

- [ ] **Step 3.1: DTO 5종 구현**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionCreateRequest.java`:
```java
package com.elipair.spacestudyship.study.timer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record TimerSessionCreateRequest(
        @Schema(description = "연결된 Todo ID (없으면 null)", example = "todo-uuid-5678")
        @Size(max = 36) String todoId,

        @Schema(description = "Todo 제목 스냅샷 (Todo 삭제 후 표시용)", example = "수학 문제 풀기")
        @Size(max = 100) String todoTitle,

        @Schema(description = "타이머 시작 시각 (ISO 8601 UTC)", example = "2026-05-25T00:00:00Z")
        @NotNull Instant startedAt,

        @Schema(description = "타이머 종료 시각 (ISO 8601 UTC)", example = "2026-05-25T01:30:00Z")
        @NotNull Instant endedAt,

        @Schema(description = "실제 공부 시간 (분, 일시정지 제외)", example = "90")
        @NotNull Integer durationMinutes
) {}
```

`SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionResponse.java`:
```java
package com.elipair.spacestudyship.study.timer.dto;

import com.elipair.spacestudyship.study.timer.entity.TimerSession;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.ZoneOffset;

@Schema(description = "타이머 세션 단건")
public record TimerSessionResponse(
        String id,
        String todoId,
        String todoTitle,
        Instant startedAt,
        Instant endedAt,
        Integer durationMinutes
) {
    public static TimerSessionResponse from(TimerSession s) {
        return new TimerSessionResponse(
                s.getId(), s.getTodoId(), s.getTodoTitle(),
                s.getStartedAt().atOffset(ZoneOffset.UTC).toInstant(),
                s.getEndedAt().atOffset(ZoneOffset.UTC).toInstant(),
                s.getDurationMinutes());
    }
}
```

`SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionCreateResponse.java`:
```java
package com.elipair.spacestudyship.study.timer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "세션 저장 응답")
public record TimerSessionCreateResponse(
        TimerSessionResponse session,
        @Schema(description = "서버에서 검증 후 충전된 연료량") Integer fuelCharged
) {}
```

`SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TimerSessionListResponse.java`:
```java
package com.elipair.spacestudyship.study.timer.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record TimerSessionListResponse(
        List<TimerSessionResponse> content,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages
) {
    public static TimerSessionListResponse from(Page<com.elipair.spacestudyship.study.timer.entity.TimerSession> page) {
        return new TimerSessionListResponse(
                page.getContent().stream().map(TimerSessionResponse::from).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
```

`SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/TodayStatsResponse.java`:
```java
package com.elipair.spacestudyship.study.timer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "오늘 공부 통계 (KST 기준)")
public record TodayStatsResponse(
        @Schema(description = "오늘 총 공부 시간 (분)") Integer totalMinutes,
        @Schema(description = "오늘 완료한 세션 수") Integer sessionCount,
        @Schema(description = "연속 공부 일수 (오늘 포함, KST 기준)") Integer streak
) {}
```

- [ ] **Step 3.2: TimerSessionService 테스트 작성**

`SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/service/TimerSessionServiceTest.java`:
```java
package com.elipair.spacestudyship.study.timer.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionCreateRequest;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionCreateResponse;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionListResponse;
import com.elipair.spacestudyship.study.timer.dto.TodayStatsResponse;
import com.elipair.spacestudyship.study.timer.entity.TimerSession;
import com.elipair.spacestudyship.study.timer.repository.TimerSessionRepository;
import com.elipair.spacestudyship.study.todo.service.TodoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimerSessionServiceTest {

    @Mock TimerSessionRepository sessionRepository;
    @Mock FuelService fuelService;
    @Mock TodoService todoService;

    TimerSessionService service;

    // 2026-05-25 12:00 UTC == 2026-05-25 21:00 KST
    Clock fixedClock = Clock.fixed(Instant.parse("2026-05-25T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new TimerSessionService(sessionRepository, fuelService, todoService, fixedClock);
    }

    private TimerSessionCreateRequest validRequest(int duration) {
        return new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z"),
                duration);
    }

    // ---------- validate (5 cases) ----------

    @Test
    @DisplayName("validate: startedAt == endedAt → INVALID_SESSION_TIME")
    void validate_sameTime_throws() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T01:00:00Z"),
                1);

        assertThatThrownBy(() -> service.create(1L, req, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SESSION_TIME);
    }

    @Test
    @DisplayName("validate: durationMinutes > 경과시간 → INVALID_DURATION")
    void validate_durationOverElapsed_throws() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T01:30:00Z"),
                31);

        assertThatThrownBy(() -> service.create(1L, req, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DURATION);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1"})
    @DisplayName("validate: durationMinutes < 1 → SESSION_TOO_SHORT")
    void validate_tooShort_throws(int duration) {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T03:00:00Z"),
                duration);

        assertThatThrownBy(() -> service.create(1L, req, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_TOO_SHORT);
    }

    @Test
    @DisplayName("validate: durationMinutes > 1440 → SESSION_TOO_LONG")
    void validate_tooLong_throws() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-23T00:00:00Z"),
                Instant.parse("2026-05-25T01:00:00Z"),
                1441);

        assertThatThrownBy(() -> service.create(1L, req, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SESSION_TOO_LONG);
    }

    @Test
    @DisplayName("validate: startedAt > now + 5분 → FUTURE_SESSION")
    void validate_future_throws() {
        // now (clock) = 2026-05-25T12:00:00Z. 5분 후 = 12:05:00. 그 후 = 12:05:01 부터 FUTURE
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T12:05:01Z"),
                Instant.parse("2026-05-25T13:00:00Z"),
                30);

        assertThatThrownBy(() -> service.create(1L, req, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.FUTURE_SESSION);
    }

    @Test
    @DisplayName("validate: startedAt == now + 5분 정확히 → 통과")
    void validate_exactlyAtSkewBoundary_passes() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                null, null,
                Instant.parse("2026-05-25T12:05:00Z"),
                Instant.parse("2026-05-25T13:00:00Z"),
                30);

        TimerSessionCreateResponse res = service.create(1L, req, null);
        assertThat(res.session().durationMinutes()).isEqualTo(30);
    }

    // ---------- create 정상 흐름 ----------

    @Test
    @DisplayName("create 정상: 세션 저장 + Fuel 충전 + (todoId 없으므로) Todo 미호출")
    void create_noTodo_chargesFuel_doesNotTouchTodo() {
        TimerSessionCreateRequest req = validRequest(60);

        TimerSessionCreateResponse res = service.create(1L, req, null);

        ArgumentCaptor<TimerSession> savedCap = ArgumentCaptor.forClass(TimerSession.class);
        verify(sessionRepository).save(savedCap.capture());
        TimerSession saved = savedCap.getValue();

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getDurationMinutes()).isEqualTo(60);
        assertThat(saved.getIdempotencyKey()).isNull();
        assertThat(saved.getId()).isNotBlank();

        // Fuel: sessionId == transactionId
        verify(fuelService).charge(
                eq(1L), eq(60), eq(FuelReason.STUDY_SESSION),
                eq(saved.getId()), eq(saved.getId()));

        // Todo는 호출되지 않아야 함
        verifyNoInteractions(todoService);

        assertThat(res.fuelCharged()).isEqualTo(60);
        assertThat(res.session().id()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("create 정상: todoId 있으면 TodoService.addActualMinutes 호출")
    void create_withTodo_callsAddActualMinutes() {
        TimerSessionCreateRequest req = new TimerSessionCreateRequest(
                "todo-1", "수학",
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z"),
                60);

        service.create(1L, req, null);

        verify(todoService).addActualMinutes(eq(1L), eq("todo-1"), eq(60));
    }

    // ---------- Idempotency ----------

    @Test
    @DisplayName("Idempotency-Key dedup: 동일 키 재요청 시 기존 세션 반환, fuel/todo 호출 0회")
    void idempotency_dedup_returnsExisting() {
        TimerSession existing = TimerSession.of(
                "existing-id", 1L, null, null,
                LocalDateTime.parse("2026-05-25T01:00:00"),
                LocalDateTime.parse("2026-05-25T02:00:00"),
                60, "idem-1");
        given(sessionRepository.findByUserIdAndIdempotencyKey(1L, "idem-1"))
                .willReturn(Optional.of(existing));

        TimerSessionCreateResponse res = service.create(1L, validRequest(60), "idem-1");

        verify(sessionRepository, never()).save(any());
        verifyNoInteractions(fuelService);
        verifyNoInteractions(todoService);
        assertThat(res.session().id()).isEqualTo("existing-id");
        assertThat(res.fuelCharged()).isEqualTo(60);
    }

    @Test
    @DisplayName("Idempotency-Key 정규화: blank → null로 취급 (dedup 안 함)")
    void idempotency_blank_normalizedToNull() {
        service.create(1L, validRequest(60), "   ");

        verify(sessionRepository, never()).findByUserIdAndIdempotencyKey(anyLong(), any());
        ArgumentCaptor<TimerSession> cap = ArgumentCaptor.forClass(TimerSession.class);
        verify(sessionRepository).save(cap.capture());
        assertThat(cap.getValue().getIdempotencyKey()).isNull();
    }

    @Test
    @DisplayName("Idempotency race: save 시 DataIntegrityViolation → 재조회 후 기존 반환")
    void idempotency_race_resolvedByReSelect() {
        // 첫 조회는 empty (race window)
        given(sessionRepository.findByUserIdAndIdempotencyKey(1L, "idem-1"))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(TimerSession.of(
                        "racer-id", 1L, null, null,
                        LocalDateTime.parse("2026-05-25T01:00:00"),
                        LocalDateTime.parse("2026-05-25T02:00:00"),
                        60, "idem-1")));
        // save는 unique violation
        given(sessionRepository.save(any(TimerSession.class)))
                .willThrow(new DataIntegrityViolationException("unique violation"));

        TimerSessionCreateResponse res = service.create(1L, validRequest(60), "idem-1");

        assertThat(res.session().id()).isEqualTo("racer-id");
        verifyNoInteractions(fuelService);
        verifyNoInteractions(todoService);
    }

    @Test
    @DisplayName("Idempotency race: save 실패했는데 재조회도 empty → 원본 예외 rethrow")
    void idempotency_race_rethrowIfStillMissing() {
        given(sessionRepository.findByUserIdAndIdempotencyKey(1L, "idem-1"))
                .willReturn(Optional.empty());
        given(sessionRepository.save(any(TimerSession.class)))
                .willThrow(new DataIntegrityViolationException("unique violation"));

        assertThatThrownBy(() -> service.create(1L, validRequest(60), "idem-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- getList ----------

    @Test
    @DisplayName("getList: 필터 인자가 서비스 → 레포로 전달, Page envelope 변환")
    void getList_passThroughAndEnvelope() {
        TimerSession s = TimerSession.of(
                UUID.randomUUID().toString(), 1L, "t-1", "title",
                LocalDateTime.parse("2026-05-25T01:00:00"),
                LocalDateTime.parse("2026-05-25T02:00:00"),
                60, null);
        Page<TimerSession> page = new PageImpl<>(List.of(s));
        given(sessionRepository.findByFilters(eq(1L), any(), any(), eq("t-1"), any(Pageable.class)))
                .willReturn(page);

        TimerSessionListResponse res = service.getList(
                1L, "2026-05-20", "2026-05-25", "t-1", 0, 20);

        assertThat(res.content()).hasSize(1);
        assertThat(res.content().get(0).id()).isEqualTo(s.getId());
    }

    // ---------- today-stats ----------

    @Test
    @DisplayName("today-stats: 빈 데이터 → 모두 0")
    void todayStats_empty() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(0);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        given(sessionRepository.findStartedAtsAfter(eq(1L), any())).willReturn(List.of());

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res).isEqualTo(new TodayStatsResponse(0, 0, 0));
    }

    @Test
    @DisplayName("today-stats: 정상 데이터 + streak 계산")
    void todayStats_withData() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(180);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(3L);
        // KST 기준 오늘 = 2026-05-25, 어제 = 5/24, 그저께 = 5/23
        given(sessionRepository.findStartedAtsAfter(eq(1L), any()))
                .willReturn(List.of(
                        // 5/25 KST = 5/24 15:00 ~ 5/25 15:00 UTC
                        LocalDateTime.parse("2026-05-25T02:00:00"),  // 5/25 KST 11:00
                        LocalDateTime.parse("2026-05-23T16:00:00"),  // 5/24 KST 01:00
                        LocalDateTime.parse("2026-05-22T16:00:00")   // 5/23 KST 01:00
                ));

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res.totalMinutes()).isEqualTo(180);
        assertThat(res.sessionCount()).isEqualTo(3);
        assertThat(res.streak()).isEqualTo(3);  // 5/23, 5/24, 5/25 연속
    }

    @Test
    @DisplayName("streak: 어제까지만 했으면 어제 기준으로 N (오늘 포함 X)")
    void streak_yesterdayLatest() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(0);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        // 어제(5/24) + 그저께(5/23) 만
        given(sessionRepository.findStartedAtsAfter(eq(1L), any()))
                .willReturn(List.of(
                        LocalDateTime.parse("2026-05-23T16:00:00"),
                        LocalDateTime.parse("2026-05-22T16:00:00")
                ));

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res.streak()).isEqualTo(2);
    }

    @Test
    @DisplayName("streak: 마지막 공부일이 어제보다 이전 → 0")
    void streak_brokenChain() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(0);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        // 그저께(5/23)만
        given(sessionRepository.findStartedAtsAfter(eq(1L), any()))
                .willReturn(List.of(LocalDateTime.parse("2026-05-22T16:00:00")));

        TodayStatsResponse res = service.getTodayStats(1L);

        assertThat(res.streak()).isZero();
    }

    @Test
    @DisplayName("streak: latest가 미래(clock skew)면 today로 클램프")
    void streak_futureLatest_clampedToToday() {
        given(sessionRepository.sumDurationBetween(eq(1L), any(), any())).willReturn(0);
        given(sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(eq(1L), any(), any()))
                .willReturn(0L);
        // now KST = 2026-05-25 21:00. 미래 = 2026-05-26 KST + 어제 KST(5/24)
        given(sessionRepository.findStartedAtsAfter(eq(1L), any()))
                .willReturn(List.of(
                        LocalDateTime.parse("2026-05-26T01:00:00"),  // KST 5/26 10:00 (미래)
                        LocalDateTime.parse("2026-05-25T01:00:00"),  // KST 5/25 10:00 (오늘)
                        LocalDateTime.parse("2026-05-23T16:00:00")   // KST 5/24 01:00 (어제)
                ));

        TodayStatsResponse res = service.getTodayStats(1L);

        // cursor = min(latest=5/26, today=5/25) = 5/25 → 5/25, 5/24 카운트 → 2 (5/26 부풀림 방지)
        assertThat(res.streak()).isEqualTo(2);
    }
}
```

- [ ] **Step 3.3: 테스트 실행 — 컴파일 실패 확인**

Run:
```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.timer.service.TimerSessionServiceTest"
```
Expected: COMPILATION FAILED (TimerSessionService, TodoService.addActualMinutes 등 없음).

- [ ] **Step 3.4: TimerSessionService 구현**

> ⚠️ **컴파일 의존성:** 본 클래스는 `todoService.addActualMinutes(...)`를 호출. Task 4의 Step 4.4 (TodoService 메서드 추가)가 선행되어야 컴파일 통과. 본 Step에서는 코드만 작성하고 다음 Task 4를 이어서 진행한 뒤 한 번에 컴파일/테스트 검증.

`SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/service/TimerSessionService.java`:
```java
package com.elipair.spacestudyship.study.timer.service;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.study.fuel.constant.FuelReason;
import com.elipair.spacestudyship.study.fuel.service.FuelService;
import com.elipair.spacestudyship.study.timer.dto.*;
import com.elipair.spacestudyship.study.timer.entity.TimerSession;
import com.elipair.spacestudyship.study.timer.repository.TimerSessionRepository;
import com.elipair.spacestudyship.study.todo.service.TodoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
public class TimerSessionService {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");
    private static final long CLOCK_SKEW_TOLERANCE_SECONDS = 300; // 5분
    private static final int STREAK_LOOKBACK_DAYS = 365;

    private final TimerSessionRepository sessionRepository;
    private final FuelService fuelService;
    private final TodoService todoService;
    private final Clock clock;

    public TimerSessionService(TimerSessionRepository sessionRepository,
                               FuelService fuelService,
                               TodoService todoService,
                               Clock clock) {
        this.sessionRepository = sessionRepository;
        this.fuelService = fuelService;
        this.todoService = todoService;
        this.clock = clock;
    }

    @Transactional
    public TimerSessionCreateResponse create(
            Long userId, TimerSessionCreateRequest request, String idempotencyKey) {

        String normalizedKey = (idempotencyKey == null || idempotencyKey.isBlank())
                ? null : idempotencyKey.trim();

        if (normalizedKey != null) {
            Optional<TimerSession> existing = sessionRepository
                    .findByUserIdAndIdempotencyKey(userId, normalizedKey);
            if (existing.isPresent()) {
                log.info("[Timer] idempotent skip | userId={}, key={}, sessionId={}",
                        userId, normalizedKey, existing.get().getId());
                return buildResponse(existing.get(), existing.get().getDurationMinutes());
            }
        }

        LocalDateTime startedAtUtc = LocalDateTime.ofInstant(request.startedAt(), ZoneOffset.UTC);
        LocalDateTime endedAtUtc   = LocalDateTime.ofInstant(request.endedAt(),   ZoneOffset.UTC);
        validate(startedAtUtc, endedAtUtc, request.durationMinutes());

        String sessionId = UUID.randomUUID().toString();
        TimerSession session = TimerSession.of(
                sessionId, userId,
                request.todoId(), request.todoTitle(),
                startedAtUtc, endedAtUtc, request.durationMinutes(),
                normalizedKey);

        try {
            sessionRepository.save(session);
        } catch (DataIntegrityViolationException e) {
            if (normalizedKey != null) {
                Optional<TimerSession> raced = sessionRepository
                        .findByUserIdAndIdempotencyKey(userId, normalizedKey);
                if (raced.isPresent()) {
                    log.info("[Timer] idempotent race resolved | userId={}, key={}",
                            userId, normalizedKey);
                    return buildResponse(raced.get(), raced.get().getDurationMinutes());
                }
            }
            throw e;
        }

        int fuelCharged = request.durationMinutes();
        fuelService.charge(userId, fuelCharged, FuelReason.STUDY_SESSION, sessionId, sessionId);

        if (request.todoId() != null) {
            todoService.addActualMinutes(userId, request.todoId(), fuelCharged);
        }

        log.info("[Timer] 세션 저장 | userId={}, sessionId={}, duration={}분, todoId={}",
                userId, sessionId, fuelCharged, request.todoId());
        return buildResponse(session, fuelCharged);
    }

    private void validate(LocalDateTime startedAt, LocalDateTime endedAt, int durationMinutes) {
        if (!endedAt.isAfter(startedAt)) {
            throw new CustomException(ErrorCode.INVALID_SESSION_TIME);
        }
        long elapsedMinutes = Duration.between(startedAt, endedAt).toMinutes();
        if (durationMinutes > elapsedMinutes) {
            throw new CustomException(ErrorCode.INVALID_DURATION);
        }
        if (durationMinutes < 1) {
            throw new CustomException(ErrorCode.SESSION_TOO_SHORT);
        }
        if (durationMinutes > 1440) {
            throw new CustomException(ErrorCode.SESSION_TOO_LONG);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (startedAt.isAfter(now.plusSeconds(CLOCK_SKEW_TOLERANCE_SECONDS))) {
            throw new CustomException(ErrorCode.FUTURE_SESSION);
        }
    }

    private TimerSessionCreateResponse buildResponse(TimerSession session, int fuelCharged) {
        return new TimerSessionCreateResponse(TimerSessionResponse.from(session), fuelCharged);
    }

    public TimerSessionListResponse getList(
            Long userId, String startDate, String endDate, String todoId,
            int page, int size) {

        LocalDateTime start = startDate == null ? null
                : LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = endDate == null ? null
                : LocalDate.parse(endDate).plusDays(1).atStartOfDay();

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<TimerSession> result = sessionRepository.findByFilters(
                userId, start, end, todoId, pageable);
        return TimerSessionListResponse.from(result);
    }

    public TodayStatsResponse getTodayStats(Long userId) {
        LocalDate todayKst = LocalDate.now(clock.withZone(ZONE_KST));
        LocalDateTime todayStartUtc    = toUtcLdt(todayKst.atStartOfDay(ZONE_KST));
        LocalDateTime tomorrowStartUtc = toUtcLdt(todayKst.plusDays(1).atStartOfDay(ZONE_KST));

        Integer totalMinutes = Optional.ofNullable(
                sessionRepository.sumDurationBetween(userId, todayStartUtc, tomorrowStartUtc))
                .orElse(0);
        long sessionCount = sessionRepository
                .countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                        userId, todayStartUtc, tomorrowStartUtc);

        LocalDateTime lookbackStart = toUtcLdt(
                todayKst.minusDays(STREAK_LOOKBACK_DAYS).atStartOfDay(ZONE_KST));
        List<LocalDateTime> startedAts = sessionRepository
                .findStartedAtsAfter(userId, lookbackStart);

        int streak = computeStreak(startedAts, todayKst);
        return new TodayStatsResponse(totalMinutes, (int) sessionCount, streak);
    }

    private LocalDateTime toUtcLdt(ZonedDateTime kst) {
        return kst.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private int computeStreak(List<LocalDateTime> startedAtsUtc, LocalDate todayKst) {
        TreeSet<LocalDate> studyDays = startedAtsUtc.stream()
                .map(ts -> ts.atZone(ZoneOffset.UTC).withZoneSameInstant(ZONE_KST).toLocalDate())
                .collect(Collectors.toCollection(TreeSet::new));
        if (studyDays.isEmpty()) return 0;

        LocalDate latest = studyDays.last();
        LocalDate cursor = latest.isAfter(todayKst) ? todayKst : latest;
        if (cursor.isBefore(todayKst.minusDays(1))) return 0;

        int streak = 0;
        while (studyDays.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
```

- [ ] **Step 3.5: 컴파일 검증 (Task 4 진행 후 보장)**

> Service만 작성한 상태에서는 `todoService.addActualMinutes` 미존재로 컴파일 실패. Task 4까지 끝낸 뒤 검증.

(여기서는 빌드를 실행하지 않고 다음 Task로 진행.)

---

## Task 4: TodoService.addActualMinutes (Todo 도메인 보강)

`Todo.actualMinutes` 누적을 lost update 없이 atomic하게 처리하는 메서드 추가. Task 3의 Service가 의존하므로 본 Task가 끝나면 Task 3와 함께 빌드/테스트 통과 가능.

**Files:**
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/repository/TodoRepository.java`
- Modify: `SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoRepositoryTest.java`
- Modify: `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java`

### Steps

- [ ] **Step 4.1: TodoRepository 쿼리 회귀 테스트 추가**

먼저 `SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoRepositoryTest.java` 의 import에 다음 두 줄이 없으면 추가:
```java
import com.elipair.spacestudyship.study.todo.entity.Todo;
import jakarta.persistence.EntityManager;
```

그리고 `@Autowired TodoRepository todoRepository;` 필드 바로 아래에 EntityManager 필드를 추가:
```java
    @Autowired
    EntityManager em;
```

그런 다음 클래스 끝(`}` 직전)에 다음 3개 테스트를 추가:
```java
    @Test
    @DisplayName("addActualMinutes: 본인 todo 누적 — null → 0+minutes, 기존 → 기존+minutes")
    void addActualMinutes_accumulates() {
        Todo t = Todo.create(
                "t-1", 1L, "수학",
                List.of("2026-05-25"),
                List.of(),
                60);
        todoRepository.saveAndFlush(t);

        int updated1 = todoRepository.addActualMinutes(1L, "t-1", 30);
        assertThat(updated1).isEqualTo(1);
        em.clear();  // 1차 캐시 비워서 DB 상태 재조회
        assertThat(todoRepository.findById("t-1").get().getActualMinutes()).isEqualTo(30);

        int updated2 = todoRepository.addActualMinutes(1L, "t-1", 45);
        assertThat(updated2).isEqualTo(1);
        em.clear();
        assertThat(todoRepository.findById("t-1").get().getActualMinutes()).isEqualTo(75);
    }

    @Test
    @DisplayName("addActualMinutes: 본인 소유 아님 → affected=0")
    void addActualMinutes_otherUser_returnsZero() {
        Todo t = Todo.create(
                "t-1", 1L, "수학",
                List.of("2026-05-25"),
                List.of(),
                60);
        todoRepository.saveAndFlush(t);

        int updated = todoRepository.addActualMinutes(2L, "t-1", 30);
        assertThat(updated).isZero();
    }

    @Test
    @DisplayName("addActualMinutes: 없는 todoId → affected=0")
    void addActualMinutes_missingTodo_returnsZero() {
        int updated = todoRepository.addActualMinutes(1L, "nope", 30);
        assertThat(updated).isZero();
    }
```

- [ ] **Step 4.2: 테스트 실행 — 컴파일 실패 확인**

Run:
```bash
./gradlew :SS-Study:test --tests "com.elipair.spacestudyship.study.todo.repository.TodoRepositoryTest"
```
Expected: COMPILATION FAILED (addActualMinutes 메서드 없음).

- [ ] **Step 4.3: TodoRepository에 addActualMinutes 쿼리 추가**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/repository/TodoRepository.java`에 import와 메서드 추가:
```java
import org.springframework.data.jpa.repository.Modifying;
```
(이미 있으면 생략)

인터페이스 내 마지막 메서드 다음에:
```java
    @Modifying
    @Query("UPDATE Todo t SET t.actualMinutes = COALESCE(t.actualMinutes, 0) + :minutes " +
           "WHERE t.id = :todoId AND t.userId = :userId")
    int addActualMinutes(@Param("userId") Long userId,
                         @Param("todoId") String todoId,
                         @Param("minutes") int minutes);
```

- [ ] **Step 4.4: TodoService에 addActualMinutes 메서드 추가**

`SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java` 의 `delete()` 메서드 뒤에 추가:
```java
    @Transactional
    public void addActualMinutes(Long userId, String todoId, int minutes) {
        if (minutes <= 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        int updated = todoRepository.addActualMinutes(userId, todoId, minutes);
        if (updated == 0) {
            throw new CustomException(ErrorCode.TODO_NOT_FOUND);
        }
        log.info("[Todo] actualMinutes 누적 | userId={}, todoId={}, +{}분", userId, todoId, minutes);
    }
```

- [ ] **Step 4.5: TodoService 단위 테스트 추가**

`SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java`의 클래스 끝(`}` 직전)에 추가:
```java
    @Test
    @org.junit.jupiter.api.DisplayName("addActualMinutes: 정상 흐름 — repository 호출 및 로그")
    void addActualMinutes_success() {
        org.mockito.BDDMockito.given(todoRepository.addActualMinutes(1L, "t-1", 30))
                .willReturn(1);

        todoService.addActualMinutes(1L, "t-1", 30);

        org.mockito.Mockito.verify(todoRepository).addActualMinutes(1L, "t-1", 30);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("addActualMinutes: minutes <= 0 → INVALID_INPUT_VALUE")
    void addActualMinutes_nonPositive_throws() {
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> todoService.addActualMinutes(1L, "t-1", 0))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("addActualMinutes: 영향 row 0 → TODO_NOT_FOUND")
    void addActualMinutes_notFound_throws() {
        org.mockito.BDDMockito.given(todoRepository.addActualMinutes(1L, "nope", 30))
                .willReturn(0);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> todoService.addActualMinutes(1L, "nope", 30))
                .isInstanceOf(com.elipair.spacestudyship.common.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.elipair.spacestudyship.common.exception.ErrorCode.TODO_NOT_FOUND);
    }
```

> 기존 `TodoServiceTest`의 필드 이름이 다르면(`todoRepository`/`todoService`) 그에 맞춰 조정. 미리 파일을 읽어 확인.

- [ ] **Step 4.6: SS-Study 전체 테스트 실행 — 통과 확인**

Run:
```bash
./gradlew :SS-Study:test
```
Expected: BUILD SUCCESSFUL — Task 2~4의 모든 테스트 (entity, repository, service) 통과.

만약 컴파일 에러가 남아있다면 import 누락 또는 필드명 mismatch 가능 — 메시지 보고 수정.

- [ ] **Step 4.7: 커밋 (Task 3 + 4 통합)**

```bash
git add SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/dto/ \
  SS-Study/src/main/java/com/elipair/spacestudyship/study/timer/service/TimerSessionService.java \
  SS-Study/src/test/java/com/elipair/spacestudyship/study/timer/service/TimerSessionServiceTest.java \
  SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/repository/TodoRepository.java \
  SS-Study/src/main/java/com/elipair/spacestudyship/study/todo/service/TodoService.java \
  SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/repository/TodoRepositoryTest.java \
  SS-Study/src/test/java/com/elipair/spacestudyship/study/todo/service/TodoServiceTest.java
git commit -m "타이머 세션 도메인 구현 : feat : DTO + TimerSessionService + TodoService.addActualMinutes (검증/Idempotency/Streak 포함) #25"
```

---

## Task 5: TimerSessionController + Swagger + Controller 테스트

3개 엔드포인트 컨트롤러와 Swagger 문서, MockMvc 테스트 작성.

**Files:**
- Create: `SS-Web/src/main/java/com/elipair/spacestudyship/controller/timer/TimerSessionController.java`
- Create: `SS-Web/src/test/java/com/elipair/spacestudyship/controller/timer/TimerSessionControllerTest.java`

### Steps

- [ ] **Step 5.1: Controller 테스트 작성**

`SS-Web/src/test/java/com/elipair/spacestudyship/controller/timer/TimerSessionControllerTest.java`:
```java
package com.elipair.spacestudyship.controller.timer;

import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.GlobalExceptionHandler;
import com.elipair.spacestudyship.study.timer.dto.*;
import com.elipair.spacestudyship.study.timer.service.TimerSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TimerSessionControllerTest {

    @Mock TimerSessionService service;
    @InjectMocks TimerSessionController controller;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver loginMemberStub = new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(LoginMember.class);
            }
            @Override public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                                    org.springframework.web.context.request.NativeWebRequest webRequest,
                                                    org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return new LoginMember(1L);
            }
        };

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(loginMemberStub)
                .setValidator(validator)
                .setMessageConverters(jsonConverter)
                .build();
    }

    // ---------- POST 정상 ----------

    @Test
    @DisplayName("POST /api/timer-sessions — 201, { session, fuelCharged }")
    void create_201() throws Exception {
        TimerSessionResponse sessionRes = new TimerSessionResponse(
                "sess-1", "todo-1", "수학",
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T02:30:00Z"),
                90);
        given(service.create(eq(1L), any(TimerSessionCreateRequest.class), any()))
                .willReturn(new TimerSessionCreateResponse(sessionRes, 90));

        String body = """
                {
                  "todoId": "todo-1",
                  "todoTitle": "수학",
                  "startedAt": "2026-05-25T01:00:00Z",
                  "endedAt": "2026-05-25T02:30:00Z",
                  "durationMinutes": 90
                }
                """;

        mockMvc.perform(post("/api/timer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.session.id").value("sess-1"))
                .andExpect(jsonPath("$.session.durationMinutes").value(90))
                .andExpect(jsonPath("$.fuelCharged").value(90));
    }

    @Test
    @DisplayName("POST: Idempotency-Key 헤더 → 서비스에 전달")
    void create_idempotencyKeyPassThrough() throws Exception {
        TimerSessionResponse sessionRes = new TimerSessionResponse(
                "sess-1", null, null,
                Instant.parse("2026-05-25T01:00:00Z"),
                Instant.parse("2026-05-25T02:00:00Z"),
                60);
        given(service.create(eq(1L), any(), eq("idem-abc")))
                .willReturn(new TimerSessionCreateResponse(sessionRes, 60));

        String body = """
                {"startedAt":"2026-05-25T01:00:00Z","endedAt":"2026-05-25T02:00:00Z","durationMinutes":60}
                """;

        mockMvc.perform(post("/api/timer-sessions")
                        .header("Idempotency-Key", "idem-abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        verify(service).create(eq(1L), any(), eq("idem-abc"));
    }

    @Test
    @DisplayName("POST: 비즈니스 검증 실패 (FUTURE_SESSION) → 400 + code")
    void create_futureSession_400() throws Exception {
        willThrow(new CustomException(ErrorCode.FUTURE_SESSION))
                .given(service).create(eq(1L), any(), any());

        String body = """
                {"startedAt":"2030-01-01T00:00:00Z","endedAt":"2030-01-01T01:00:00Z","durationMinutes":60}
                """;

        mockMvc.perform(post("/api/timer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUTURE_SESSION"));
    }

    @Test
    @DisplayName("POST: NotNull 위반 (durationMinutes 누락) → 400 INVALID_INPUT_VALUE")
    void create_missingField_400() throws Exception {
        String body = """
                {"startedAt":"2026-05-25T01:00:00Z","endedAt":"2026-05-25T02:00:00Z"}
                """;

        mockMvc.perform(post("/api/timer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("POST: 본문 파싱 실패 → 400 INVALID_REQUEST_BODY")
    void create_malformedBody_400() throws Exception {
        mockMvc.perform(post("/api/timer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    @DisplayName("POST: TODO_NOT_FOUND → 404")
    void create_todoNotFound_404() throws Exception {
        willThrow(new CustomException(ErrorCode.TODO_NOT_FOUND))
                .given(service).create(eq(1L), any(), any());

        String body = """
                {"todoId":"nope","startedAt":"2026-05-25T01:00:00Z","endedAt":"2026-05-25T02:00:00Z","durationMinutes":60}
                """;

        mockMvc.perform(post("/api/timer-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODO_NOT_FOUND"));
    }

    // ---------- GET 목록 ----------

    @Test
    @DisplayName("GET /api/timer-sessions — 200, Page envelope, 인자 그대로 전달")
    void getList_200() throws Exception {
        given(service.getList(eq(1L), eq("2026-05-20"), eq("2026-05-25"), eq("t-1"), eq(0), eq(20)))
                .willReturn(new TimerSessionListResponse(
                        List.of(new TimerSessionResponse(
                                "sess-1", "t-1", "수학",
                                Instant.parse("2026-05-25T01:00:00Z"),
                                Instant.parse("2026-05-25T02:00:00Z"),
                                60)),
                        0, 20, 1L, 1));

        mockMvc.perform(get("/api/timer-sessions")
                        .param("startDate", "2026-05-20")
                        .param("endDate", "2026-05-25")
                        .param("todoId", "t-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("sess-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET: 잘못된 날짜 포맷 → 400")
    void getList_badDate_400() throws Exception {
        mockMvc.perform(get("/api/timer-sessions").param("startDate", "2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("GET: size > 100 → 400")
    void getList_sizeOverMax_400() throws Exception {
        mockMvc.perform(get("/api/timer-sessions").param("size", "200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("GET: page < 0 → 400")
    void getList_negativePage_400() throws Exception {
        mockMvc.perform(get("/api/timer-sessions").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    // ---------- today-stats ----------

    @Test
    @DisplayName("GET /api/timer-sessions/today-stats — 200")
    void todayStats_200() throws Exception {
        given(service.getTodayStats(1L))
                .willReturn(new TodayStatsResponse(180, 3, 7));

        mockMvc.perform(get("/api/timer-sessions/today-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes").value(180))
                .andExpect(jsonPath("$.sessionCount").value(3))
                .andExpect(jsonPath("$.streak").value(7));
    }
}
```

- [ ] **Step 5.2: 테스트 실행 — 컴파일 실패 확인**

Run:
```bash
./gradlew :SS-Web:test --tests "com.elipair.spacestudyship.controller.timer.TimerSessionControllerTest"
```
Expected: COMPILATION FAILED (TimerSessionController 없음).

- [ ] **Step 5.3: TimerSessionController 구현**

`SS-Web/src/main/java/com/elipair/spacestudyship/controller/timer/TimerSessionController.java`:
```java
package com.elipair.spacestudyship.controller.timer;

import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.common.exception.ErrorResponse;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionCreateRequest;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionCreateResponse;
import com.elipair.spacestudyship.study.timer.dto.TimerSessionListResponse;
import com.elipair.spacestudyship.study.timer.dto.TodayStatsResponse;
import com.elipair.spacestudyship.study.timer.service.TimerSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Tag(name = "Timer", description = "공부 타이머 세션 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/timer-sessions")
public class TimerSessionController {

    private final TimerSessionService timerSessionService;

    @Operation(summary = "세션 기록 저장",
            description = """
                타이머 종료 시 세션을 저장합니다.
                서버에서 시간 유효성 5단계 검증 후, 통과 시 연료를 자동 충전하고
                연결된 Todo의 actualMinutes를 누적합니다 (단일 트랜잭션).

                ### Idempotency
                헤더 `Idempotency-Key`를 보내면 동일 키 재요청 시 기존 세션을 반환합니다 (중복 충전 방지).
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "저장 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TimerSessionCreateResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "session": {
                                        "id":"sess-uuid",
                                        "todoId":"todo-1",
                                        "todoTitle":"수학",
                                        "startedAt":"2026-05-25T01:00:00Z",
                                        "endedAt":"2026-05-25T02:30:00Z",
                                        "durationMinutes":90
                                      },
                                      "fuelCharged":90
                                    }
                                    """))),
            @ApiResponse(responseCode = "400", description = "검증 실패",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "INVALID_SESSION_TIME", value = "{\"code\":\"INVALID_SESSION_TIME\",\"message\":\"시작 시각이 종료 시각보다 늦거나 같습니다.\"}"),
                                    @ExampleObject(name = "INVALID_DURATION",     value = "{\"code\":\"INVALID_DURATION\",\"message\":\"공부 시간이 시작/종료 시각 간격보다 큽니다.\"}"),
                                    @ExampleObject(name = "SESSION_TOO_SHORT",    value = "{\"code\":\"SESSION_TOO_SHORT\",\"message\":\"공부 시간은 1분 이상이어야 합니다.\"}"),
                                    @ExampleObject(name = "SESSION_TOO_LONG",     value = "{\"code\":\"SESSION_TOO_LONG\",\"message\":\"공부 시간은 24시간(1440분)을 초과할 수 없습니다.\"}"),
                                    @ExampleObject(name = "FUTURE_SESSION",       value = "{\"code\":\"FUTURE_SESSION\",\"message\":\"미래 시각의 세션은 저장할 수 없습니다.\"}")
                            })),
            @ApiResponse(responseCode = "401", description = "인증 필요",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "연결된 Todo가 본인 소유 아님 / 존재하지 않음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"TODO_NOT_FOUND\",\"message\":\"해당 할 일을 찾을 수 없습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<TimerSessionCreateResponse> create(
            @AuthMember LoginMember loginMember,
            @Valid @RequestBody TimerSessionCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        TimerSessionCreateResponse response = timerSessionService.create(
                loginMember.memberId(), request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "세션 목록 조회",
            description = """
                ### Query Parameters
                - startDate / endDate: YYYY-MM-DD (선택). 종료일 포함 반열림 [start, end+1)
                - todoId: 특정 Todo에 연결된 세션만 (선택)
                - page: 기본 0
                - size: 기본 20, 최대 100

                정렬: startedAt 내림차순 (최신순) 고정.
                """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TimerSessionListResponse.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 query parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_INPUT_VALUE\",\"message\":\"입력값이 유효하지 않습니다.\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping
    public ResponseEntity<TimerSessionListResponse> getList(
            @AuthMember LoginMember loginMember,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String todoId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        validateDateParam(startDate);
        validateDateParam(endDate);
        if (page < 0) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        if (size < 1 || size > 100) throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);

        return ResponseEntity.ok(timerSessionService.getList(
                loginMember.memberId(), startDate, endDate, todoId, page, size));
    }

    @Operation(summary = "오늘 공부 통계",
            description = "KST(Asia/Seoul) 기준 오늘의 총 분 / 세션 수 / 연속 일수(streak)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TodayStatsResponse.class),
                            examples = @ExampleObject(value = "{\"totalMinutes\":180,\"sessionCount\":3,\"streak\":7}"))),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    @GetMapping("/today-stats")
    public ResponseEntity<TodayStatsResponse> getTodayStats(
            @AuthMember LoginMember loginMember) {
        return ResponseEntity.ok(
                timerSessionService.getTodayStats(loginMember.memberId()));
    }

    private void validateDateParam(String date) {
        if (date == null) return;
        try { LocalDate.parse(date); }
        catch (DateTimeParseException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
```

- [ ] **Step 5.4: 테스트 실행 — 통과 확인**

Run:
```bash
./gradlew :SS-Web:test --tests "com.elipair.spacestudyship.controller.timer.TimerSessionControllerTest"
```
Expected: 12 tests passed.

- [ ] **Step 5.5: 전체 빌드 검증**

Run:
```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5.6: 커밋**

```bash
git add SS-Web/src/main/java/com/elipair/spacestudyship/controller/timer/TimerSessionController.java \
  SS-Web/src/test/java/com/elipair/spacestudyship/controller/timer/TimerSessionControllerTest.java
git commit -m "타이머 세션 도메인 구현 : feat : TimerSessionController + Swagger 풀세트 + MockMvc 테스트 #25"
```

---

## Task 6: CLAUDE.md 마이그레이션 이력 업데이트

**Files:**
- Modify: `CLAUDE.md`

### Steps

- [ ] **Step 6.1: 마이그레이션 표에 0.0.39 row 추가**

`CLAUDE.md`의 `### 현재 마이그레이션 이력` 표 끝에 다음 row 추가 (마지막 `| 0.0.36 | ...` 줄 다음):
```markdown
| 0.0.39 | `V0_0_39__add_timer_sessions.sql` | `timer_sessions` 테이블 생성 (FK CASCADE, CHECK 제약 3종, 부분 unique 인덱스 on idempotency_key) |
```

- [ ] **Step 6.2: 커밋**

```bash
git add CLAUDE.md
git commit -m "타이머 세션 도메인 구현 : docs : CLAUDE.md 마이그레이션 이력 표에 0.0.39 추가 #25"
```

---

## Final Verification

- [ ] **F.1: 전체 테스트 실행**

```bash
./gradlew clean test
```
Expected: BUILD SUCCESSFUL. (모든 모듈, 모든 테스트 통과 — Testcontainers 기동 포함 ~3분)

- [ ] **F.2: 애플리케이션 기동 + 수동 Swagger 검증 (선택)**

```bash
./gradlew :SS-Web:bootRun
```
브라우저로 `http://localhost:8080/swagger-ui.html` 접속하여:
- `Timer` 태그에 3개 엔드포인트 노출 확인
- POST 시 정상/검증 실패 응답 매트릭스 확인
- Idempotency-Key 헤더 옵션 확인
- today-stats 응답 형식 확인

- [ ] **F.3: 커밋 히스토리 확인**

```bash
git log --oneline -10
```
Expected: 6개 신규 커밋 — chore, feat (entity/repo), feat (dto/service+todo), feat (controller), docs, plus the docs spec/plan commits.

---

## Notes for the Engineer

### TDD 흐름 요약
각 Task는 다음 사이클을 반복:
1. 테스트 작성 (RED)
2. `./gradlew :SS-X:test --tests "패키지.클래스"`로 실패 확인
3. 최소 구현 (GREEN)
4. 테스트 재실행, 통과 확인
5. (선택) 리팩토링
6. 커밋

### 통합 의존성 주의
- Task 3 (TimerSessionService)는 Task 4 (TodoService.addActualMinutes)에 컴파일 의존. 두 Task를 한 사이클로 묶어 마지막에 함께 빌드/테스트 실행 → 둘 다 통과하면 커밋
- Task 2의 Repository 통합 테스트는 Testcontainers 사용 → 첫 실행 시 PostgreSQL 16 이미지 다운로드 발생 (1회성, 수 분 소요 가능)

### 컨벤션 주의 (CLAUDE.md)
- 커밋 메시지: `타이머 세션 도메인 구현 : {type} : {설명} #25` (이모지 금지)
- DTO는 Record, Entity는 `@Builder` + protected NoArgsConstructor
- DTO 위치: SS-Study의 `study/timer/dto/` (Controller/Service 공유)
- Controller는 SS-Web에만 위치
- 한 version.yml 버전당 마이그레이션 파일 1개만

### 이미 해결된 의존성 (Spec 섹션 2.3 참조)
- `FuelService.charge(userId, amount, reason, referenceId, transactionId)` — 이미 idempotency 처리 完
- `FuelReason.STUDY_SESSION` enum + CHECK 제약 이미 존재
- `MemberCreatedEvent` 기반 신규 회원 UserFuel 초기화 이미 동작 (FUEL_NOT_INITIALIZED 노출 가능성 낮음)
- `BaseTimeEntity` 의 `@CreationTimestamp`/`@UpdateTimestamp` — TimerSession 응답에는 createdAt 미포함이므로 #36 flush 이슈 해당 없음

### 실패/막힘 시 행동
- 테스트 실행 결과 OUTPUT을 읽고 어디가 실패하는지 정확히 보기
- import 누락 → IDE 자동 import 또는 메시지 참고
- Testcontainers 기동 실패 → Docker daemon 실행 여부 확인
- 컴파일 막힘이 의존 Task 누락 때문이라면 의존 Task 진행 후 재시도
- 동시성 race 테스트가 가끔 깜빡이면 — 실제 race는 production에서만 의미. 테스트는 mock으로 시뮬레이션이므로 결정적이어야 함. 시퀀스 검토.
