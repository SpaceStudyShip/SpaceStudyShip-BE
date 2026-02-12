package com.elipair.spacestudyship.auth.controller.dto;

import com.elipair.spacestudyship.auth.service.dto.LoginCommand;
import com.elipair.spacestudyship.member.entity.SocialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(

        @NotNull(message = "소셜 플랫폼 정보는 필수입니다.")
        SocialType socialType,

        @NotBlank(message = "소셜 인증 토큰(ID Token)은 필수입니다.")
        String idToken
) {
    public LoginCommand toCommand() {
        return new LoginCommand(this.socialType, this.idToken);
    }
}
