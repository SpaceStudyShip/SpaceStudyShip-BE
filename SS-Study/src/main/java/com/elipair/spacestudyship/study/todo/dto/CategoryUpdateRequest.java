package com.elipair.spacestudyship.study.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "카테고리 부분 수정 요청 — 전송하지 않은 필드는 기존 값 유지")
public record CategoryUpdateRequest(

        @Schema(description = "카테고리 이름 (1~20자, 공백만 입력 불가)", nullable = true)
        @Size(min = 1, max = 20)
        @Pattern(regexp = ".*\\S.*", message = "name: 공백만으로 구성될 수 없습니다.")
        String name,

        @Schema(description = "아이콘 식별자", nullable = true)
        String iconId,

        @Schema(description = "맵 가로 위치 (0.0~1.0)", nullable = true)
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double positionX,

        @Schema(description = "맵 세로 위치 (0.0~1.0)", nullable = true)
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double positionY
) {
}
