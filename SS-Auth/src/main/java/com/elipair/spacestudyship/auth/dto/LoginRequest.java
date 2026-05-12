package com.elipair.spacestudyship.auth.dto;

import com.elipair.spacestudyship.auth.constant.DeviceType;
import com.elipair.spacestudyship.member.constant.SocialType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "소셜 로그인 요청 본문")
public record LoginRequest(
        @Schema(description = "소셜 로그인 플랫폼. 지원: GOOGLE, APPLE, KAKAO.",
                example = "GOOGLE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "소셜 플랫폼 정보는 필수입니다.") SocialType socialType,

        @Schema(description = "Firebase에서 발급받은 ID Token.",
                example = "eyJhbGciOiJSUzI1NiIs...", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "소셜 인증 토큰(ID Token)은 필수입니다.") String idToken,

        @Schema(description = "Firebase Cloud Messaging 디바이스 토큰.",
                example = "dK3mL9xRTp2...", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "FCM 토큰은 필수입니다.")
        @Size(max = 255, message = "FCM 토큰은 255자 이하여야 합니다.") String fcmToken,

        @Schema(description = "디바이스 OS 타입.",
                example = "IOS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "디바이스 타입은 필수입니다.") DeviceType deviceType,

        @Schema(description = "디바이스 고유 식별자(UUID).",
                example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "디바이스 식별자는 필수입니다.")
        @Size(max = 255, message = "디바이스 식별자는 255자 이하여야 합니다.")
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "디바이스 식별자는 UUID 형식이어야 합니다.") String deviceId
) {}
