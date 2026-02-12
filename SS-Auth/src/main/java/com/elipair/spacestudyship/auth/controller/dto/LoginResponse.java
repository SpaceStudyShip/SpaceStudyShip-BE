package com.elipair.spacestudyship.auth.controller.dto;

import com.elipair.spacestudyship.auth.domain.Tokens;
import com.elipair.spacestudyship.auth.service.dto.LoginResult;

public record LoginResponse(
        Long memberId,
        String nickname,
        Tokens tokens,
        boolean isNewMember
) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(
                result.member().getId(),
                result.member().getNickname(),
                result.tokens(),
                result.isNewMember()
        );
    }
}
