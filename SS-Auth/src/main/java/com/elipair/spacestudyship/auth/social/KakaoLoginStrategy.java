package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.member.entity.SocialType;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class KakaoLoginStrategy implements SocialLoginStrategy {

    @Override
    public String validateAndGetSocialId(String socialIdToken) {
        // TODO: 카카오 로그인 연동 구현
        // 클라이언트에서 받은 idToken으로 카카오 리소스 서버로부터 socialId를 가져온다.
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "KAKAO_SOCIAL_ID_" + random.nextInt(100_000);
    }

    @Override
    public SocialType getSocialType() {
        return SocialType.KAKAO;
    }
}
