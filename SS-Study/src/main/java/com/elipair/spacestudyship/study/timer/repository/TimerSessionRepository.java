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

    /**
     * PostgreSQL JDBC 드라이버가 nullable 파라미터의 타입을 추론하지 못하는 이슈로
     * `:param IS NULL` 대신 `CAST(:param AS Type) IS NULL` 패턴을 사용한다.
     * FuelTransactionRepository와 동일한 해결 방식.
     */
    @Query("""
        SELECT s FROM TimerSession s
        WHERE s.userId = :userId
          AND (CAST(:start AS LocalDateTime)  IS NULL OR s.startedAt >= :start)
          AND (CAST(:end   AS LocalDateTime)  IS NULL OR s.startedAt <  :end)
          AND (CAST(:todoId AS String)        IS NULL OR s.todoId = :todoId)
        """)
    Page<TimerSession> findByFilters(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("todoId") String todoId,
            Pageable pageable);

    // JPQL SUM(Integer state-field) → Long 반환이 JPA 스펙. COALESCE 기본값도 0L로 맞춘다.
    @Query("SELECT COALESCE(SUM(s.durationMinutes), 0L) FROM TimerSession s " +
           "WHERE s.userId = :userId AND s.startedAt >= :start AND s.startedAt < :end")
    Long sumDurationBetween(@Param("userId") Long userId,
                            @Param("start") LocalDateTime start,
                            @Param("end") LocalDateTime end);

    long countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            Long userId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT s.startedAt FROM TimerSession s " +
           "WHERE s.userId = :userId AND s.startedAt >= :start")
    List<LocalDateTime> findStartedAtsAfter(@Param("userId") Long userId,
                                            @Param("start") LocalDateTime start);

    // 전체 누적 분: SUM(Integer) → Long, COALESCE로 NULL 방지
    @Query("SELECT COALESCE(SUM(s.durationMinutes), 0L) FROM TimerSession s " +
           "WHERE s.userId = :userId")
    Long sumDurationByUserId(@Param("userId") Long userId);

    // 전체 세션 수: Spring Data 명명 규칙
    long countByUserId(Long userId);
}
