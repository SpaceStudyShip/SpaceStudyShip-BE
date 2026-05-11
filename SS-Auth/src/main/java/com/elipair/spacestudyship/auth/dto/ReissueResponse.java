package com.elipair.spacestudyship.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "토큰 재발급 응답. 새 Access/Refresh Token이 함께 발급되며, 클라이언트는 두 토큰 모두 교체 저장해야 합니다.")
public record ReissueResponse(
        @Schema(description = "새로 발급된 JWT 토큰 쌍.")
        Tokens tokens
) {}
