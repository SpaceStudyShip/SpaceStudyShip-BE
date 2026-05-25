package com.elipair.spacestudyship.study.timer.repository;

import com.elipair.spacestudyship.study.StudyTestApplication;
import com.elipair.spacestudyship.study.timer.entity.TimerSession;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Savepoint;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = StudyTestApplication.class)
@Transactional
class TimerSessionRepositoryTest {

    @Autowired
    TimerSessionRepository repository;

    @Autowired
    EntityManager em;

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

    /**
     * PostgreSQL에서 constraint violation 후 트랜잭션이 aborted 상태가 되므로
     * JDBC Savepoint를 사용해 충돌 INSERT 구간만 롤백하고 외부 트랜잭션을 유지한다.
     */
    @Test
    @DisplayName("partial unique index: 동일 (user, key) 중복 INSERT 실패, key=NULL은 다중 허용")
    void partialUniqueIndex() {
        repository.saveAndFlush(
                session(1L, LocalDateTime.parse("2026-05-25T01:00:00"), 30, null, "idem-1"));

        // 동일 (user, key) 두번째 INSERT는 savepoint 구간에서 실패 후 rollback to savepoint.
        // PostgreSQL unique_violation SQLState(23505)만 "중복 위반"으로 식별 — 다른 SQLException은 rethrow.
        // PreparedStatement는 try-with-resources로 명시 close.
        String sql = "INSERT INTO timer_sessions (id, user_id, todo_id, todo_title, started_at, ended_at, " +
                "duration_minutes, idempotency_key, created_at, updated_at) " +
                "VALUES (?, 1, NULL, NULL, " +
                "'2026-05-25 02:00:00', '2026-05-25 02:30:00', 30, 'idem-1', NOW(), NOW())";
        boolean constraintViolated = em.unwrap(Session.class).doReturningWork(conn -> {
            Savepoint sp = conn.setSavepoint("dup_check");
            try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.executeUpdate();
                conn.releaseSavepoint(sp);
                return false;
            } catch (java.sql.SQLException e) {
                conn.rollback(sp);
                if ("23505".equals(e.getSQLState())) {
                    return true; // PostgreSQL unique_violation 확인
                }
                throw e;
            }
        });
        assertThat(constraintViolated).isTrue();

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
                .isEqualTo(LocalDateTime.parse("2026-05-25T02:00:00"));
    }

    @Test
    @DisplayName("findByFilters: 날짜 범위 [start, end) 반열림")
    void findByFilters_dateRange() {
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-23T15:00:00"), 30, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-24T15:00:00"), 30, null, null));
        repository.saveAndFlush(session(1L, LocalDateTime.parse("2026-05-25T15:00:00"), 30, null, null));

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

        Long sum = repository.sumDurationBetween(1L,
                LocalDateTime.parse("2026-05-24T00:00:00"),
                LocalDateTime.parse("2026-05-25T00:00:00"));
        assertThat(sum).isEqualTo(90L);

        Long none = repository.sumDurationBetween(1L,
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
