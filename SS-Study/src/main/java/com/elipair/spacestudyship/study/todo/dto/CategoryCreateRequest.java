package com.elipair.spacestudyship.study.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "카테고리 생성 요청")
public record CategoryCreateRequest(

        @Schema(description = "클라이언트 UUID (없으면 서버 생성)", nullable = true,
                example = "cat-uuid-3")
        String id,

        @Schema(description = "카테고리 이름 (1~20자)", example = "수학")
        @NotBlank
        @Size(max = 20)
        String name,

        @Schema(description = "아이콘 식별자", nullable = true, example = "math_icon")
        String iconId,

        @Schema(description = "맵 가로 위치 (0.0~1.0)", nullable = true, example = "0.3")
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double positionX,

        @Schema(description = "맵 세로 위치 (0.0~1.0)", nullable = true, example = "0.5")
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        Double positionY
) {
}
