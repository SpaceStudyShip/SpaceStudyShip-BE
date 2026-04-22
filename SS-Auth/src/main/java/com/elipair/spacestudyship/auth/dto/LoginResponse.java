package com.elipair.spacestudyship.auth.dto;

public record LoginResponse(
        Long memberId,
        String nickname,
        Tokens tokens,
        boolean isNewMember
) {}
