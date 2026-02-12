package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.member.entity.SocialType;

public interface SocialLoginStrategy {

    String validateAndGetSocialId(String socialIdToken);

    SocialType getSocialType();
}
