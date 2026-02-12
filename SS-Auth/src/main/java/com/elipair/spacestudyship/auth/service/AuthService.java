package com.elipair.spacestudyship.auth.service;

import com.elipair.spacestudyship.auth.domain.Tokens;
import com.elipair.spacestudyship.auth.jwt.JwtTokenProvider;
import com.elipair.spacestudyship.auth.repository.RefreshTokenRepository;
import com.elipair.spacestudyship.auth.service.dto.LoginCommand;
import com.elipair.spacestudyship.auth.service.dto.LoginResult;
import com.elipair.spacestudyship.auth.social.SocialLoginStrategy;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.entity.Member;
import com.elipair.spacestudyship.member.entity.SocialType;
import com.elipair.spacestudyship.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAXIMUM_NICKNAME_GENERATE_RETRY_COUNT = 10;

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RandomNicknameGenerator randomNicknameGenerator;
    private final Map<SocialType, SocialLoginStrategy> socialLoginStrategies;

    /**
     * 소셜 로그인
     * - 신규 회원: 랜덤 닉네임 부여 후 DB insert
     * - 기존 회원: 토큰만 재발급
     */
    @Transactional
    public LoginResult login(LoginCommand command) {
        String socialId = getSocialId(command.socialType(), command.idToken());
        AuthMemberData authMemberData = findOrRegisterMember(socialId, command.socialType());
        Member member = authMemberData.member;
        Tokens tokens = issueTokens(member);

        return LoginResult.of(member, authMemberData.isNewMember, tokens);
    }

    private String getSocialId(SocialType socialType, String idToken) {
        SocialLoginStrategy strategy = socialLoginStrategies.get(socialType);
        if (strategy == null) {
            throw new CustomException(ErrorCode.UNSUPPORTED_SOCIAL_TYPE);
        }
        return strategy.validateAndGetSocialId(idToken);
    }

    private AuthMemberData findOrRegisterMember(String socialId, SocialType socialType) {
        return memberRepository.findBySocialIdAndSocialType(socialId, socialType)
                .map(member -> new AuthMemberData(member, false))
                .orElseGet(() -> {
                    String nickname = generateUniqueNickname();
                    Member newMember = Member.signUp(socialId, socialType, nickname);
                    memberRepository.save(newMember);

                    log.info("[SignUp] 신규 회원가입 성공 | memberId={}, nickname={}, socialType={}",
                            newMember.getId(), nickname, socialType);
                    return new AuthMemberData(newMember, true);
                });
    }

    private String generateUniqueNickname() {
        int retryCount = 0;
        String nickname;
        do {
            nickname = randomNicknameGenerator.generate();
            retryCount++;
            if (retryCount > MAXIMUM_NICKNAME_GENERATE_RETRY_COUNT) {
                log.warn("[SignUp] 닉네임 생성 재시도 횟수 초과");
                throw new CustomException(ErrorCode.NICKNAME_GENERATION_FAILED);
            }
        } while (memberRepository.existsByNickname(nickname));

        return nickname;
    }

    private Tokens issueTokens(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = jwtTokenProvider.createRefreshToken(member);
        refreshTokenRepository.save(member.getId(), refreshToken, jwtTokenProvider.getRefreshTokenExpirationMillis());
        return new Tokens(accessToken, refreshToken);
    }

    /**
     * Access Token 재발급
     */
    @Transactional(readOnly = true)
    public Tokens reissueTokens(String refreshToken) {
        Long memberId = jwtTokenProvider.getMemberIdFromRefreshToken(refreshToken);

        String storedRefreshToken = refreshTokenRepository.findByMemberId(memberId);
        if (!refreshToken.equals(storedRefreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        Member member = memberRepository.getByMemberId(memberId);
        return issueTokens(member);
    }

    /**
     * 로그아웃 - 저장된 Refresh Token 삭제
     */
    @Transactional
    public void logout(String refreshToken) {
        jwtTokenProvider.getMemberIdFromRefreshTokenSafely(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    private record AuthMemberData(
            Member member,
            boolean isNewMember
    ) {
    }
}
