package com.elipair.spacestudyship.auth.service;

import com.elipair.spacestudyship.auth.dto.*;
import com.elipair.spacestudyship.auth.entity.UserDevice;
import com.elipair.spacestudyship.auth.jwt.JwtTokenProvider;
import com.elipair.spacestudyship.auth.jwt.RefreshTokenHasher;
import com.elipair.spacestudyship.auth.jwt.RefreshTokenPayload;
import com.elipair.spacestudyship.auth.repository.UserDeviceRepository;
import com.elipair.spacestudyship.auth.social.SocialLoginStrategy;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.entity.Member;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.elipair.spacestudyship.member.repository.MemberRepository;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAXIMUM_NICKNAME_GENERATE_RETRY_COUNT = 10;
    private static final int MAX_DEVICES_PER_MEMBER = 10;

    private final MemberRepository memberRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RandomNicknameGenerator randomNicknameGenerator;
    private final Map<SocialType, SocialLoginStrategy> socialLoginStrategies;
    private final FirebaseAuth firebaseAuth;

    /**
     * 소셜 로그인
     * - 신규 회원: 랜덤 닉네임 부여 후 DB insert
     * - 기존 회원: 토큰만 재발급
     * - 디바이스 정보(fcmToken, deviceType, deviceId)를 user_devices에 upsert
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String socialId = getSocialId(request.socialType(), request.idToken());
        AuthMemberDto authMemberData = findOrRegisterMember(socialId, request.socialType());
        Member member = authMemberData.member();

        String accessToken = jwtTokenProvider.createAccessToken(member);
        String refreshToken = jwtTokenProvider.createRefreshToken(member, request.deviceId());
        String refreshTokenHash = RefreshTokenHasher.hash(refreshToken);

        upsertUserDevice(member.getId(), request, refreshTokenHash);

        return new LoginResponse(member.getId(), member.getNickname(),
                new Tokens(accessToken, refreshToken), authMemberData.isNewMember());
    }

    private void upsertUserDevice(Long memberId, LoginRequest request, String refreshTokenHash) {
        userDeviceRepository.findByMemberIdAndDeviceId(memberId, request.deviceId())
                .ifPresentOrElse(
                        device -> device.renewLogin(request.deviceType(), request.fcmToken(), refreshTokenHash),
                        () -> {
                            if (userDeviceRepository.countByMemberId(memberId) >= MAX_DEVICES_PER_MEMBER) {
                                throw new CustomException(ErrorCode.DEVICE_LIMIT_EXCEEDED);
                            }
                            userDeviceRepository.save(UserDevice.register(
                                    memberId, request.deviceId(), request.deviceType(),
                                    request.fcmToken(), refreshTokenHash));
                        }
                );
    }

    private String getSocialId(SocialType socialType, String idToken) {
        SocialLoginStrategy strategy = socialLoginStrategies.get(socialType);
        if (strategy == null) {
            throw new CustomException(ErrorCode.UNSUPPORTED_SOCIAL_TYPE);
        }
        return strategy.validateAndGetSocialId(idToken);
    }

    private AuthMemberDto findOrRegisterMember(String socialId, SocialType socialType) {
        return memberRepository.findBySocialIdAndSocialType(socialId, socialType)
                .map(member -> new AuthMemberDto(member, false))
                .orElseGet(() -> {
                    String nickname = generateUniqueNickname();
                    Member newMember = Member.signUp(socialId, socialType, nickname);
                    memberRepository.save(newMember);

                    log.info("[SignUp] 신규 회원가입 성공 | memberId={}, nickname={}, socialType={}",
                            newMember.getId(), nickname, socialType);
                    return new AuthMemberDto(newMember, true);
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

    /**
     * Access Token 재발급 (디바이스 단위)
     */
    @Transactional
    public ReissueResponse reissue(ReissueRequest request) {
        RefreshTokenPayload payload = jwtTokenProvider.parseRefreshToken(request.refreshToken());

        UserDevice device = userDeviceRepository
                .findByMemberIdAndDeviceId(payload.memberId(), payload.deviceId())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN));

        if (!device.getRefreshTokenHash().equals(RefreshTokenHasher.hash(request.refreshToken()))) {
            userDeviceRepository.delete(device);
            log.warn("[Security] Refresh Token 불일치 - 강제 로그아웃 처리 | memberId={}, deviceId={}",
                    payload.memberId(), payload.deviceId());
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }

        Member member = memberRepository.getByMemberId(payload.memberId());
        String newAccess = jwtTokenProvider.createAccessToken(member);
        String newRefresh = jwtTokenProvider.createRefreshToken(member, payload.deviceId());

        device.rotateRefreshTokenHash(RefreshTokenHasher.hash(newRefresh));
        return new ReissueResponse(new Tokens(newAccess, newRefresh));
    }

    /**
     * 로그아웃 - 해당 디바이스 row만 삭제. 다른 디바이스 세션 영향 없음.
     */
    @Transactional
    public void logout(String refreshToken) {
        jwtTokenProvider.parseRefreshTokenSafely(refreshToken)
                .ifPresent(payload -> userDeviceRepository
                        .deleteByMemberIdAndDeviceId(payload.memberId(), payload.deviceId()));
    }

    /**
     * 닉네임 중복 확인
     */
    @Transactional(readOnly = true)
    public CheckNicknameResponse checkNickname(String nickname) {
        boolean exists = memberRepository.existsByNickname(nickname);
        return new CheckNicknameResponse(!exists);
    }

    /**
     * 닉네임 변경
     */
    @Transactional
    public UpdateNicknameResponse updateNickname(Long memberId, UpdateNicknameRequest request) {
        Member member = memberRepository.getByMemberId(memberId);
        String newNickname = request.nickname();

        if (member.getNickname().equals(newNickname)) {
            return new UpdateNicknameResponse(member.getNickname());
        }

        if (memberRepository.existsByNickname(newNickname)) {
            throw new CustomException(ErrorCode.DUPLICATED_NICKNAME);
        }

        try {
            member.updateNickname(newNickname);
            memberRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATED_NICKNAME);
        }
        return new UpdateNicknameResponse(member.getNickname());
    }

    /**
     * 회원 탈퇴 - DB(members) 삭제 + FK CASCADE로 user_devices 자동 삭제 + Firebase 사용자 삭제.
     * Firebase 예외는 멱등성 유지를 위해 모두 무시(로그만 기록).
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member != null) {
            memberRepository.delete(member);
            deleteFirebaseUserSafely(memberId, member.getSocialId());
        }
    }

    private void deleteFirebaseUserSafely(Long memberId, String socialId) {
        try {
            firebaseAuth.deleteUser(socialId);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                log.warn("[Withdraw] Firebase 사용자 이미 없음 | memberId={}, socialId={}",
                        memberId, socialId);
            } else {
                log.error("[Withdraw] Firebase 사용자 삭제 실패 | memberId={}, socialId={}, error={}",
                        memberId, socialId, e.getMessage());
            }
        }
    }
}
