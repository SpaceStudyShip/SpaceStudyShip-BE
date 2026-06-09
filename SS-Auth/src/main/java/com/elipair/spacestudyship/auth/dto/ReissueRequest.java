package com.elipair.spacestudyship.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "토큰 재발급 요청 본문")
public record ReissueRequest(
        @Schema(
                description = "현재 보유한 Refresh Token. 서버에서 검증 후 새 Access/Refresh Token을 발급합니다 (Refresh Token Rotation).",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzE...",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Refresh Token은 필수입니다.") String refreshToken
) {}
