package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleLoginStrategy implements SocialLoginStrategy {

    private final FirebaseAuth firebaseAuth;

    @Override
    public String validateAndGetSocialId(String socialIdToken) {
        try {
            return firebaseAuth.verifyIdToken(socialIdToken).getUid();
        } catch (FirebaseAuthException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }

    @Override
    public SocialType getSocialType() {
        return SocialType.GOOGLE;
    }
}
