package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.member.entity.SocialType;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class AppleLoginStrategy implements SocialLoginStrategy {

    @Override
    public String validateAndGetSocialId(String socialIdToken) {
        // TODO: 애플 로그인 연동 구현
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "APPLE_SOCIAL_ID_" + random.nextInt(100_000);
    }

    @Override
    public SocialType getSocialType() {
        return SocialType.APPLE;
    }
}
