package com.elipair.spacestudyship.study.todo.dto;

import com.elipair.spacestudyship.study.todo.entity.TodoCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Schema(description = "카테고리 응답")
public record CategoryResponse(
        @Schema(description = "카테고리 ID", example = "cat-uuid-1") String id,
        @Schema(description = "이름", example = "수학") String name,
        @Schema(description = "아이콘 식별자", nullable = true) String iconId,
        @Schema(description = "맵 가로 위치", nullable = true) Double positionX,
        @Schema(description = "맵 세로 위치", nullable = true) Double positionY,
        @Schema(description = "생성 시각 (ISO 8601 UTC)") String createdAt,
        @Schema(description = "마지막 수정 시각 (ISO 8601 UTC)", nullable = true) String updatedAt
) {
    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ISO_INSTANT;

    public static CategoryResponse from(TodoCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getIconId(),
                category.getPositionX(),
                category.getPositionY(),
                formatUtc(category.getCreatedAt()),
                formatUtc(category.getUpdatedAt())
        );
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
