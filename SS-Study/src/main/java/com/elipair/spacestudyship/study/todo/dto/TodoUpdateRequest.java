package com.elipair.spacestudyship.study.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "할 일 부분 수정 요청 — 전송하지 않은 필드는 기존 값 유지")
public record TodoUpdateRequest(

        @Schema(description = "제목 (1~100자, 공백만 입력 불가)", nullable = true)
        @Size(min = 1, max = 100)
        @Pattern(regexp = ".*\\S.*", message = "title: 공백만으로 구성될 수 없습니다.")
        String title,

        @Schema(description = "예정 날짜 목록 (YYYY-MM-DD)", nullable = true)
        List<@Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "scheduledDates: YYYY-MM-DD 형식이어야 합니다.") String> scheduledDates,

        @Schema(description = "완료 날짜 목록 (YYYY-MM-DD)", nullable = true)
        List<@Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "completedDates: YYYY-MM-DD 형식이어야 합니다.") String> completedDates,

        @Schema(description = "카테고리 ID 목록", nullable = true)
        List<@Pattern(regexp = "[a-zA-Z0-9-]+", message = "categoryIds: 영숫자와 하이픈만 허용합니다.") String> categoryIds,

        @Schema(description = "예상 소요 시간(분)", nullable = true)
        @Min(1)
        Integer estimatedMinutes,

        @Schema(description = "실제 소요 시간(분)", nullable = true)
        @Min(0)
        Integer actualMinutes
) {
}
