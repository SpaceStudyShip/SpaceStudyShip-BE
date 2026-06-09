package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleLoginStrategy implements SocialLoginStrategy {

    private final FirebaseAuth firebaseAuth;

    @Override
    public String validateAndGetSocialId(String socialIdToken) {
        try {
            return firebaseAuth.verifyIdToken(socialIdToken).getUid();
        } catch (FirebaseAuthException e) {
            log.warn("[GoogleLogin] Firebase 토큰 검증 실패 | reason={}", e.getMessage());
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    @Override
    public SocialType getSocialType() {
        return SocialType.GOOGLE;
    }
}
