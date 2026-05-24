package com.elipair.spacestudyship.study.fuel.dto;

import com.elipair.spacestudyship.study.fuel.entity.UserFuel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "연료 잔량 응답")
public record FuelResponse(
        @Schema(description = "현재 보유 연료", example = "350") Integer currentFuel,
        @Schema(description = "누적 충전량", example = "1200") Integer totalCharged,
        @Schema(description = "누적 소비량", example = "850") Integer totalConsumed,
        @Schema(description = "미동기화 시간(분) - 향후 확장용, 현재 항상 0", example = "0") Integer pendingMinutes,
        @Schema(description = "마지막 변동 시각 (ISO 8601 UTC)", example = "2026-04-16T10:30:00Z") String lastUpdatedAt
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static FuelResponse from(UserFuel fuel) {
        return new FuelResponse(
                fuel.getCurrentFuel(),
                fuel.getTotalCharged(),
                fuel.getTotalConsumed(),
                fuel.getPendingMinutes(),
                formatUtc(fuel.getUpdatedAt())
        );
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
