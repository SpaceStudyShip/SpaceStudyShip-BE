package com.elipair.spacestudyship.study.exploration.dto;

import com.elipair.spacestudyship.study.exploration.entity.ExplorationNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "행성 응답")
public record PlanetResponse(
        String id, String name, String nodeType, int depth, String icon,
        @Schema(nullable = true) String parentId,
        @Schema(nullable = true) String prerequisiteId,
        int requiredFuel, boolean isUnlocked, boolean isCleared, int sortOrder,
        String description, double mapX, double mapY,
        @Schema(nullable = true, example = "2026-04-01T00:00:00Z") String unlockedAt,
        ProgressDto progress
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static PlanetResponse of(ExplorationNode n, boolean isUnlocked, boolean isCleared,
                                    int clearedChildren, int totalChildren, double progressRatio,
                                    LocalDateTime unlockedAt) {
        return new PlanetResponse(
                n.getId(), n.getName(), n.getNodeType().value(), n.getDepth(), n.getIcon(),
                n.getParentId(), n.getPrerequisiteNodeId(), n.getRequiredFuel(),
                isUnlocked, isCleared, n.getSortOrder(), n.getDescription(), n.getMapX(), n.getMapY(),
                formatUtc(unlockedAt),
                new ProgressDto(clearedChildren, totalChildren, progressRatio));
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
