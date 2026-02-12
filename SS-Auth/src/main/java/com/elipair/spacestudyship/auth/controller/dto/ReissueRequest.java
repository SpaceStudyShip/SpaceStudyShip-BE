package com.elipair.spacestudyship.auth.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record ReissueRequest(
        @NotBlank(message = "Refresh Token은 필수입니다.")
        String refreshToken
) {
}
