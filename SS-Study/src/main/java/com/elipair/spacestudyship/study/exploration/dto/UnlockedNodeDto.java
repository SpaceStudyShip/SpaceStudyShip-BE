package com.elipair.spacestudyship.study.exploration.dto;

import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import com.elipair.spacestudyship.study.exploration.entity.UserExploration;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "해금된 노드 요약")
public record UnlockedNodeDto(
        String id, String name, boolean isUnlocked, boolean isCleared,
        @Schema(example = "2026-04-16T11:00:00Z") String unlockedAt
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static UnlockedNodeDto of(ExplorationNode node, UserExploration progress, boolean cleared) {
        return new UnlockedNodeDto(
                node.getId(), node.getName(), true, cleared,
                formatUtc(progress.getUnlockedAt()));
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
