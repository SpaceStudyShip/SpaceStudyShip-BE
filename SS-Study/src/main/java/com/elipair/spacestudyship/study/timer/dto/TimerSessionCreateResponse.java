package com.elipair.spacestudyship.study.timer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "세션 저장 응답")
public record TimerSessionCreateResponse(
        TimerSessionResponse session,
        @Schema(description = "서버에서 검증 후 충전된 연료량") Integer fuelCharged
) {}
