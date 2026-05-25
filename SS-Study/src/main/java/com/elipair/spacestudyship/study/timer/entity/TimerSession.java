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
