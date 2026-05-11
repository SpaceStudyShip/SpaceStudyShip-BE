package com.elipair.spacestudyship.auth.jwt;

import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.elipair.spacestudyship.member.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private Member member;

    @BeforeEach
    void setUp() {
        String accessSecret = Base64.getEncoder().encodeToString(
                "test-access-secret-with-32-bytes-or-more-length-padding".getBytes());
        String refreshSecret = Base64.getEncoder().encodeToString(
                "test-refresh-secret-with-32-bytes-or-more-length-padding".getBytes());

        JwtProperties props = new JwtProperties(
                new JwtProperties.TokenInfo(accessSecret, Duration.ofMinutes(30)),
                new JwtProperties.TokenInfo(refreshSecret, Duration.ofDays(14))
        );
        jwtTokenProvider = new JwtTokenProvider(props);

        member = Member.builder()
                .id(42L)
                .socialId("social-id")
                .socialType(SocialType.GOOGLE)
                .nickname("테스터")
                .build();
    }

    @Test
    @DisplayName("createRefreshToken: deviceId claim 포함하여 발급, parseRefreshToken으로 추출 가능")
    void createAndParseRefreshToken() {
        String deviceId = "device-uuid-123";
        String token = jwtTokenProvider.createRefreshToken(member, deviceId);
        RefreshTokenPayload payload = jwtTokenProvider.parseRefreshToken(token);

        assertThat(payload.memberId()).isEqualTo(42L);
        assertThat(payload.deviceId()).isEqualTo(deviceId);
    }

    @Test
    @DisplayName("parseRefreshToken: 위변조된 토큰은 INVALID_TOKEN 예외")
    void parseRefreshToken_invalid() {
        assertThatThrownBy(() -> jwtTokenProvider.parseRefreshToken("not-a-jwt"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("parseRefreshTokenSafely: 정상 토큰 → Optional 값 반환")
    void parseRefreshTokenSafely_valid() {
        String token = jwtTokenProvider.createRefreshToken(member, "device-1");

        Optional<RefreshTokenPayload> result = jwtTokenProvider.parseRefreshTokenSafely(token);

        assertThat(result).isPresent();
        assertThat(result.get().memberId()).isEqualTo(42L);
        assertThat(result.get().deviceId()).isEqualTo("device-1");
    }

    @Test
    @DisplayName("parseRefreshTokenSafely: 위변조 토큰 → Optional.empty")
    void parseRefreshTokenSafely_invalid() {
        Optional<RefreshTokenPayload> result = jwtTokenProvider.parseRefreshTokenSafely("garbage");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("createAccessToken / getMemberIdFromAccessToken: Access Token 동작 변경 없음")
    void accessTokenStillWorks() {
        String accessToken = jwtTokenProvider.createAccessToken(member);
        Long extracted = jwtTokenProvider.getMemberIdFromAccessToken(accessToken);
        assertThat(extracted).isEqualTo(42L);
    }
}
