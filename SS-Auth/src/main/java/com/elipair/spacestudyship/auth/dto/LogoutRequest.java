package com.elipair.spacestudyship.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "로그아웃 요청 본문. 서버에 저장된 Refresh Token을 무효화하기 위해 전달합니다.")
public record LogoutRequest(
        @Schema(
                description = "현재 디바이스의 Refresh Token. 서버는 이 토큰에서 memberId를 추출해 해당 세션을 삭제합니다.",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzE...",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Refresh Token은 필수입니다.") String refreshToken
) {}
