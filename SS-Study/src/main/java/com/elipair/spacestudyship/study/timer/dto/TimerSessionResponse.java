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
