package com.elipair.spacestudyship.study.timer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "오늘 공부 통계 (KST 기준)")
public record TodayStatsResponse(
        @Schema(description = "오늘 총 공부 시간 (분)") Integer totalMinutes,
        @Schema(description = "오늘 완료한 세션 수") Integer sessionCount,
        @Schema(description = "연속 공부 일수 (오늘 포함, KST 기준)") Integer streak
) {}
