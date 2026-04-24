package com.elipair.spacestudyship.auth.service;

import com.elipair.spacestudyship.auth.dto.CheckNicknameResponse;
import com.elipair.spacestudyship.auth.jwt.JwtTokenProvider;
import com.elipair.spacestudyship.auth.repository.RefreshTokenRepository;
import com.elipair.spacestudyship.auth.social.SocialLoginStrategy;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.elipair.spacestudyship.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    MemberRepository memberRepository;
    @Mock
    RefreshTokenRepository refreshTokenRepository;
    @Mock
    JwtTokenProvider jwtTokenProvider;
    @Mock
    RandomNicknameGenerator randomNicknameGenerator;
    @Mock
    Map<SocialType, SocialLoginStrategy> socialLoginStrategies;

    @InjectMocks
    AuthService authService;

    @Test
    @DisplayName("checkNickname: DB에 닉네임이 없으면 available=true")
    void checkNickname_available() {
        // given
        String nickname = "우주탐험가";
        given(memberRepository.existsByNickname(nickname)).willReturn(false);

        // when
        CheckNicknameResponse response = authService.checkNickname(nickname);

        // then
        assertThat(response.available()).isTrue();
    }

    @Test
    @DisplayName("checkNickname: DB에 닉네임이 있으면 available=false")
    void checkNickname_notAvailable() {
        // given
        String nickname = "우주탐험가";
        given(memberRepository.existsByNickname(nickname)).willReturn(true);

        // when
        CheckNicknameResponse response = authService.checkNickname(nickname);

        // then
        assertThat(response.available()).isFalse();
    }
}
