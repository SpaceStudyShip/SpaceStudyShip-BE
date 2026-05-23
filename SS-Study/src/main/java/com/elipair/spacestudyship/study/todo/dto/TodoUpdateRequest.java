package com.elipair.spacestudyship.study.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "할 일 부분 수정 요청 — 전송하지 않은 필드는 기존 값 유지")
public record TodoUpdateRequest(

        @Schema(description = "제목 (1~100자)", nullable = true)
        @Size(min = 1, max = 100)
        String title,

        @Schema(description = "예정 날짜 목록 (YYYY-MM-DD)", nullable = true)
        List<String> scheduledDates,

        @Schema(description = "완료 날짜 목록 (YYYY-MM-DD)", nullable = true)
        List<String> completedDates,

        @Schema(description = "카테고리 ID 목록", nullable = true)
        List<String> categoryIds,

        @Schema(description = "예상 소요 시간(분)", nullable = true)
        @Min(1)
        Integer estimatedMinutes,

        @Schema(description = "실제 소요 시간(분)", nullable = true)
        @Min(0)
        Integer actualMinutes
) {
}
