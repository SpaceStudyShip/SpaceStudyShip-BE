package com.elipair.spacestudyship.study.fuel.dto;

import com.elipair.spacestudyship.study.fuel.entity.FuelTransaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "연료 거래 내역")
public record FuelTransactionResponse(
        @Schema(example = "tx-uuid-1234") String id,

        @Schema(description = "charge 또는 consume",
                allowableValues = {"charge", "consume"}, example = "charge")
        String type,

        @Schema(example = "90") Integer amount,

        @Schema(description = "거래 사유",
                allowableValues = {"STUDY_SESSION", "EXPLORATION_UNLOCK"},
                example = "STUDY_SESSION")
        String reason,

        @Schema(nullable = true, example = "session-uuid-5678") String referenceId,
        @Schema(example = "350") Integer balanceAfter,
        @Schema(example = "2026-04-16T10:30:00Z") String createdAt
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static FuelTransactionResponse from(FuelTransaction tx) {
        return new FuelTransactionResponse(
                tx.getId(),
                tx.getType().name().toLowerCase(),
                tx.getAmount(),
                tx.getReason().name(),
                tx.getReferenceId(),
                tx.getBalanceAfter(),
                formatUtc(tx.getCreatedAt())
        );
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
