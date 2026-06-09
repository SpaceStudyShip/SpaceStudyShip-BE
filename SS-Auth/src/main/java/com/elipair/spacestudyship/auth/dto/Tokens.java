package com.elipair.spacestudyship.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT 토큰 쌍. accessToken은 `Authorization: Bearer <token>` 헤더에 실어 보내고, refreshToken은 만료 시 재발급에 사용합니다.")
public record Tokens(
        @Schema(
                description = "JWT Access Token. 보호된 API 호출 시 Authorization 헤더에 사용.",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzE..."
        )
        String accessToken,

        @Schema(
                description = "JWT Refresh Token. Access Token 만료 시 `POST /api/auth/reissue`로 재발급 받는 데 사용.",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzE..."
        )
        String refreshToken
) {}
