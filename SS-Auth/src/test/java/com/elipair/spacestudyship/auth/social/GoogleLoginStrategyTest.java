package com.elipair.spacestudyship.auth.social;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GoogleLoginStrategyTest {

    @Mock
    FirebaseAuth firebaseAuth;

    @InjectMocks
    GoogleLoginStrategy strategy;

    @Test
    @DisplayName("validateAndGetSocialId: 유효한 토큰이면 Firebase UID 반환")
    void validateAndGetSocialId_valid() throws FirebaseAuthException {
        FirebaseToken token = mock(FirebaseToken.class);
        given(token.getUid()).willReturn("firebase-uid-google-1");
        given(firebaseAuth.verifyIdToken("valid-google-token")).willReturn(token);

        String socialId = strategy.validateAndGetSocialId("valid-google-token");

        assertThat(socialId).isEqualTo("firebase-uid-google-1");
    }

    @Test
    @DisplayName("validateAndGetSocialId: Firebase 검증 실패 시 INVALID_TOKEN")
    void validateAndGetSocialId_invalid() throws FirebaseAuthException {
        FirebaseAuthException ex = mock(FirebaseAuthException.class);
        given(firebaseAuth.verifyIdToken("invalid-token")).willThrow(ex);

        assertThatThrownBy(() -> strategy.validateAndGetSocialId("invalid-token"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("getSocialType: GOOGLE 반환")
    void getSocialType() {
        assertThat(strategy.getSocialType()).isEqualTo(SocialType.GOOGLE);
    }
}
