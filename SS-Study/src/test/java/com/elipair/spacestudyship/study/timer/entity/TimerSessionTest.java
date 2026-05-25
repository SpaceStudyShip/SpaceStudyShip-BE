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
