package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.member.constant.SocialType;

public interface SocialLoginStrategy {

    String validateAndGetSocialId(String socialIdToken);

    SocialType getSocialType();
}
