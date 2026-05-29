package com.elipair.spacestudyship.study.exploration.dto;

import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "지역 응답")
public record RegionResponse(
        String id, String name, String nodeType, int depth, String icon,
        @Schema(nullable = true) String parentId,
        int requiredFuel, boolean isUnlocked, boolean isCleared, int sortOrder,
        String description, double mapX, double mapY,
        @Schema(nullable = true, example = "2026-04-05T15:30:00Z") String unlockedAt
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static RegionResponse of(ExplorationNode n, boolean isUnlocked, boolean isCleared,
                                    LocalDateTime unlockedAt) {
        return new RegionResponse(
                n.getId(), n.getName(), n.getNodeType().value(), n.getDepth(), n.getIcon(),
                n.getParentId(), n.getRequiredFuel(), isUnlocked, isCleared,
                n.getSortOrder(), n.getDescription(), n.getMapX(), n.getMapY(),
                formatUtc(unlockedAt));
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
