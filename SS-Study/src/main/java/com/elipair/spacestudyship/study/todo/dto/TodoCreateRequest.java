package com.elipair.spacestudyship.study.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "할 일 생성 요청")
public record TodoCreateRequest(

        @Schema(description = "클라이언트 UUID v4 (없으면 서버 생성)", nullable = true,
                example = "550e8400-e29b-41d4-a716-446655440000")
        String id,

        @Schema(description = "제목 (1~100자)", example = "수학 문제 풀기")
        @NotBlank
        @Size(max = 100)
        String title,

        @Schema(description = "카테고리 ID 목록 (기본 [])", example = "[\"cat-uuid-1\"]")
        List<@Pattern(regexp = "[a-zA-Z0-9-]+", message = "categoryIds: 영숫자와 하이픈만 허용합니다.") String> categoryIds,

        @Schema(description = "예상 소요 시간(분, 1 이상)", nullable = true, example = "60")
        @Min(1)
        Integer estimatedMinutes,

        @Schema(description = "예정 날짜 목록 (YYYY-MM-DD)", example = "[\"2026-04-16\"]")
        List<@Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "scheduledDates: YYYY-MM-DD 형식이어야 합니다.") String> scheduledDates
) {
}
