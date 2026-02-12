package com.elipair.spacestudyship.auth.service.dto;

import com.elipair.spacestudyship.auth.domain.Tokens;
import com.elipair.spacestudyship.member.entity.Member;

public record LoginResult(
        Member member,
        boolean isNewMember,
        Tokens tokens
) {
    public static LoginResult of(Member member, boolean isNewMember, Tokens tokens) {
        return new LoginResult(member, isNewMember, tokens);
    }
}
