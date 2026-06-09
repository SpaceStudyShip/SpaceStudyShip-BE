package com.elipair.spacestudyship.study.todo.dto;

import com.elipair.spacestudyship.study.todo.entity.Todo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Schema(description = "할 일 응답")
public record TodoResponse(
        @Schema(description = "Todo ID", example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "제목") String title,

        @Schema(description = "예정 날짜 목록") List<String> scheduledDates,

        @Schema(description = "완료 날짜 목록") List<String> completedDates,

        @Schema(description = "카테고리 ID 목록") List<String> categoryIds,

        @Schema(description = "예상 소요 시간(분)", nullable = true) Integer estimatedMinutes,

        @Schema(description = "실제 소요 시간(분)", nullable = true) Integer actualMinutes,

        @Schema(description = "생성 시각 (ISO 8601 UTC)") String createdAt,

        @Schema(description = "마지막 수정 시각 (ISO 8601 UTC)") String updatedAt
) {
    private static final DateTimeFormatter ISO_UTC = DateTimeFormatter.ISO_INSTANT;

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getScheduledDates(),
                todo.getCompletedDates(),
                todo.getCategoryIds(),
                todo.getEstimatedMinutes(),
                todo.getActualMinutes(),
                formatUtc(todo.getCreatedAt()),
                formatUtc(todo.getUpdatedAt())
        );
    }

    private static String formatUtc(LocalDateTime time) {
        return time == null ? null : ISO_UTC.format(time.toInstant(ZoneOffset.UTC));
    }
}
