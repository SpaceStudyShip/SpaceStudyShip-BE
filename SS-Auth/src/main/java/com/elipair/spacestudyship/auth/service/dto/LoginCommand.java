package com.elipair.spacestudyship.auth.service.dto;

import com.elipair.spacestudyship.member.entity.SocialType;

public record LoginCommand(
        SocialType socialType,
        String idToken
) {
}
