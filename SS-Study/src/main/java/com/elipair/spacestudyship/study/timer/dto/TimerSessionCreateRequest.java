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
