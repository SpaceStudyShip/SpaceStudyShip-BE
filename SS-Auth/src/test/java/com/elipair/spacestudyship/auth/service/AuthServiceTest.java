package com.elipair.spacestudyship.auth.service;

import com.elipair.spacestudyship.auth.dto.CheckNicknameResponse;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameRequest;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameResponse;
import com.elipair.spacestudyship.auth.jwt.JwtTokenProvider;
import com.elipair.spacestudyship.auth.repository.RefreshTokenRepository;
import com.elipair.spacestudyship.auth.social.SocialLoginStrategy;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.elipair.spacestudyship.member.entity.Member;
import com.elipair.spacestudyship.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Test
    @DisplayName("updateNickname: 중복 없으면 닉네임 변경 성공")
    void updateNickname_success() {
        // given
        Long memberId = 1L;
        String newNickname = "우주탐험가";
        UpdateNicknameRequest request = new UpdateNicknameRequest(newNickname);
        Member member = Member.builder()
                .id(memberId)
                .socialId("social-id")
                .socialType(SocialType.GOOGLE)
                .nickname("기존닉네임")
                .build();
        given(memberRepository.existsByNickname(newNickname)).willReturn(false);
        given(memberRepository.getByMemberId(memberId)).willReturn(member);

        // when
        UpdateNicknameResponse response = authService.updateNickname(memberId, request);

        // then
        assertThat(response.nickname()).isEqualTo(newNickname);
        assertThat(member.getNickname()).isEqualTo(newNickname);
    }

    @Test
    @DisplayName("updateNickname: 이미 사용 중인 닉네임이면 DUPLICATED_NICKNAME")
    void updateNickname_duplicated() {
        // given
        Long memberId = 1L;
        String newNickname = "우주탐험가";
        UpdateNicknameRequest request = new UpdateNicknameRequest(newNickname);
        given(memberRepository.existsByNickname(newNickname)).willReturn(true);

        // when / then
        assertThatThrownBy(() -> authService.updateNickname(memberId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATED_NICKNAME);
        verify(memberRepository, never()).getByMemberId(any());
    }

    @Test
    @DisplayName("updateNickname: 회원이 없으면 MEMBER_NOT_FOUND")
    void updateNickname_memberNotFound() {
        // given
        Long memberId = 1L;
        String newNickname = "우주탐험가";
        UpdateNicknameRequest request = new UpdateNicknameRequest(newNickname);
        given(memberRepository.existsByNickname(newNickname)).willReturn(false);
        given(memberRepository.getByMemberId(memberId)).willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        // when / then
        assertThatThrownBy(() -> authService.updateNickname(memberId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }
}
