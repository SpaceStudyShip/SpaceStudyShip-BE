package com.elipair.spacestudyship.auth.service;

import com.elipair.spacestudyship.auth.constant.DeviceType;
import com.elipair.spacestudyship.auth.dto.CheckNicknameResponse;
import com.elipair.spacestudyship.auth.dto.LoginRequest;
import com.elipair.spacestudyship.auth.dto.LoginResponse;
import com.elipair.spacestudyship.auth.dto.ReissueRequest;
import com.elipair.spacestudyship.auth.dto.ReissueResponse;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameRequest;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameResponse;
import com.elipair.spacestudyship.auth.entity.UserDevice;
import com.elipair.spacestudyship.auth.jwt.JwtTokenProvider;
import com.elipair.spacestudyship.auth.jwt.RefreshTokenPayload;
import com.elipair.spacestudyship.auth.repository.UserDeviceRepository;
import com.elipair.spacestudyship.auth.social.SocialLoginStrategy;
import com.elipair.spacestudyship.common.exception.CustomException;
import com.elipair.spacestudyship.common.exception.ErrorCode;
import com.elipair.spacestudyship.member.constant.SocialType;
import com.elipair.spacestudyship.member.entity.Member;
import com.elipair.spacestudyship.member.repository.MemberRepository;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    MemberRepository memberRepository;
    @Mock
    UserDeviceRepository userDeviceRepository;
    @Mock
    JwtTokenProvider jwtTokenProvider;
    @Mock
    RandomNicknameGenerator randomNicknameGenerator;
    @Mock
    Map<SocialType, SocialLoginStrategy> socialLoginStrategies;
    @Mock
    FirebaseAuth firebaseAuth;

    @InjectMocks
    AuthService authService;

    // ===== login =====

    @Test
    @DisplayName("login: 기존 회원 + 신규 디바이스 → user_devices에 새 row insert")
    void login_existingMember_newDevice() {
        SocialType socialType = SocialType.GOOGLE;
        LoginRequest request = new LoginRequest(socialType, "id-token", "fcm-1", DeviceType.IOS, "device-1");

        SocialLoginStrategy strategy = mock(SocialLoginStrategy.class);
        given(socialLoginStrategies.get(socialType)).willReturn(strategy);
        given(strategy.validateAndGetSocialId("id-token")).willReturn("social-id-1");

        Member member = Member.builder()
                .id(10L).socialId("social-id-1").socialType(socialType).nickname("기존회원").build();
        given(memberRepository.findBySocialIdAndSocialType("social-id-1", socialType))
                .willReturn(Optional.of(member));

        given(jwtTokenProvider.createAccessToken(member)).willReturn("access-1");
        given(jwtTokenProvider.createRefreshToken(member, "device-1")).willReturn("refresh-1");
        given(userDeviceRepository.findByMemberIdAndDeviceId(10L, "device-1"))
                .willReturn(Optional.empty());

        LoginResponse response = authService.login(request);

        assertThat(response.memberId()).isEqualTo(10L);
        assertThat(response.tokens().accessToken()).isEqualTo("access-1");
        assertThat(response.tokens().refreshToken()).isEqualTo("refresh-1");
        assertThat(response.isNewMember()).isFalse();
        then(userDeviceRepository).should().save(any(UserDevice.class));
    }

    @Test
    @DisplayName("login: 기존 회원 + 기존 디바이스 → 같은 row 갱신, save() 호출 없음")
    void login_existingMember_existingDevice() {
        SocialType socialType = SocialType.GOOGLE;
        LoginRequest request = new LoginRequest(socialType, "id-token", "fcm-NEW", DeviceType.IOS, "device-1");

        SocialLoginStrategy strategy = mock(SocialLoginStrategy.class);
        given(socialLoginStrategies.get(socialType)).willReturn(strategy);
        given(strategy.validateAndGetSocialId("id-token")).willReturn("social-id-1");

        Member member = Member.builder()
                .id(10L).socialId("social-id-1").socialType(socialType).nickname("기존회원").build();
        given(memberRepository.findBySocialIdAndSocialType("social-id-1", socialType))
                .willReturn(Optional.of(member));

        given(jwtTokenProvider.createAccessToken(member)).willReturn("access-NEW");
        given(jwtTokenProvider.createRefreshToken(member, "device-1")).willReturn("refresh-NEW");

        UserDevice existing = UserDevice.register(10L, "device-1", DeviceType.ANDROID, "fcm-OLD", "refresh-OLD");
        given(userDeviceRepository.findByMemberIdAndDeviceId(10L, "device-1"))
                .willReturn(Optional.of(existing));

        authService.login(request);

        assertThat(existing.getFcmToken()).isEqualTo("fcm-NEW");
        assertThat(existing.getRefreshToken()).isEqualTo("refresh-NEW");
        assertThat(existing.getDeviceType()).isEqualTo(DeviceType.IOS);
        then(userDeviceRepository).should(never()).save(any(UserDevice.class));
    }

    // ===== reissue =====

    @Test
    @DisplayName("reissue: DB의 refresh_token과 일치하면 새 토큰 발급 + DB 갱신, deviceId 유지")
    void reissue_success() {
        String oldRefresh = "refresh-OLD";
        ReissueRequest request = new ReissueRequest(oldRefresh);

        given(jwtTokenProvider.parseRefreshToken(oldRefresh))
                .willReturn(new RefreshTokenPayload(10L, "device-1"));
        UserDevice device = UserDevice.register(10L, "device-1", DeviceType.IOS, "fcm", oldRefresh);
        given(userDeviceRepository.findByMemberIdAndDeviceId(10L, "device-1"))
                .willReturn(Optional.of(device));
        Member member = Member.builder()
                .id(10L).socialId("s").socialType(SocialType.GOOGLE).nickname("닉").build();
        given(memberRepository.getByMemberId(10L)).willReturn(member);
        given(jwtTokenProvider.createAccessToken(member)).willReturn("access-NEW");
        given(jwtTokenProvider.createRefreshToken(member, "device-1")).willReturn("refresh-NEW");

        ReissueResponse response = authService.reissue(request);

        assertThat(response.tokens().accessToken()).isEqualTo("access-NEW");
        assertThat(response.tokens().refreshToken()).isEqualTo("refresh-NEW");
        assertThat(device.getRefreshToken()).isEqualTo("refresh-NEW");
    }

    @Test
    @DisplayName("reissue: DB의 refresh_token과 불일치 → 해당 디바이스 row 삭제 + INVALID_TOKEN")
    void reissue_tokenMismatch_forceLogout() {
        String incomingRefresh = "refresh-FORGED";
        ReissueRequest request = new ReissueRequest(incomingRefresh);

        given(jwtTokenProvider.parseRefreshToken(incomingRefresh))
                .willReturn(new RefreshTokenPayload(10L, "device-1"));
        UserDevice device = UserDevice.register(10L, "device-1", DeviceType.IOS, "fcm", "refresh-CURRENT");
        given(userDeviceRepository.findByMemberIdAndDeviceId(10L, "device-1"))
                .willReturn(Optional.of(device));

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
        then(userDeviceRepository).should().delete(device);
    }

    @Test
    @DisplayName("reissue: user_devices에 해당 디바이스 row 없으면 INVALID_TOKEN")
    void reissue_deviceNotFound() {
        String incoming = "refresh-X";
        ReissueRequest request = new ReissueRequest(incoming);

        given(jwtTokenProvider.parseRefreshToken(incoming))
                .willReturn(new RefreshTokenPayload(10L, "device-gone"));
        given(userDeviceRepository.findByMemberIdAndDeviceId(10L, "device-gone"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    // ===== logout =====

    @Test
    @DisplayName("logout: refresh token 파싱 성공 시 해당 (member, device) row 삭제")
    void logout_deletesOnlyTargetDevice() {
        String refreshToken = "refresh-1";
        given(jwtTokenProvider.parseRefreshTokenSafely(refreshToken))
                .willReturn(Optional.of(new RefreshTokenPayload(10L, "device-1")));

        authService.logout(refreshToken);

        then(userDeviceRepository).should().deleteByMemberIdAndDeviceId(10L, "device-1");
    }

    @Test
    @DisplayName("logout: 위변조 등으로 파싱 불가능하면 아무 동작 안 함 (멱등)")
    void logout_invalidToken_noop() {
        given(jwtTokenProvider.parseRefreshTokenSafely("garbage"))
                .willReturn(Optional.empty());

        authService.logout("garbage");

        then(userDeviceRepository).should(never()).deleteByMemberIdAndDeviceId(any(), any());
    }

    // ===== checkNickname / updateNickname (기존 유지) =====

    @Test
    @DisplayName("checkNickname: DB에 닉네임이 없으면 available=true")
    void checkNickname_available() {
        String nickname = "우주탐험가";
        given(memberRepository.existsByNickname(nickname)).willReturn(false);

        CheckNicknameResponse response = authService.checkNickname(nickname);

        assertThat(response.available()).isTrue();
    }

    @Test
    @DisplayName("checkNickname: DB에 닉네임이 있으면 available=false")
    void checkNickname_notAvailable() {
        String nickname = "우주탐험가";
        given(memberRepository.existsByNickname(nickname)).willReturn(true);

        CheckNicknameResponse response = authService.checkNickname(nickname);

        assertThat(response.available()).isFalse();
    }

    @Test
    @DisplayName("updateNickname: 중복 없으면 닉네임 변경 성공")
    void updateNickname_success() {
        Long memberId = 1L;
        String newNickname = "우주탐험가";
        UpdateNicknameRequest request = new UpdateNicknameRequest(newNickname);
        Member member = Member.builder()
                .id(memberId).socialId("social-id").socialType(SocialType.GOOGLE).nickname("기존닉네임").build();
        given(memberRepository.getByMemberId(memberId)).willReturn(member);
        given(memberRepository.existsByNickname(newNickname)).willReturn(false);

        UpdateNicknameResponse response = authService.updateNickname(memberId, request);

        assertThat(response.nickname()).isEqualTo(newNickname);
        assertThat(member.getNickname()).isEqualTo(newNickname);
        verify(memberRepository).flush();
    }

    @Test
    @DisplayName("updateNickname: 본인 현재 닉네임과 같으면 중복 검사 없이 그대로 통과")
    void updateNickname_sameAsCurrent() {
        Long memberId = 1L;
        String currentNickname = "우주탐험가";
        UpdateNicknameRequest request = new UpdateNicknameRequest(currentNickname);
        Member member = Member.builder()
                .id(memberId).socialId("social-id").socialType(SocialType.GOOGLE).nickname(currentNickname).build();
        given(memberRepository.getByMemberId(memberId)).willReturn(member);

        UpdateNicknameResponse response = authService.updateNickname(memberId, request);

        assertThat(response.nickname()).isEqualTo(currentNickname);
        verify(memberRepository, never()).existsByNickname(any());
        verify(memberRepository, never()).flush();
    }

    @Test
    @DisplayName("updateNickname: 이미 사용 중인 닉네임이면 DUPLICATED_NICKNAME")
    void updateNickname_duplicated() {
        Long memberId = 1L;
        String newNickname = "우주탐험가";
        UpdateNicknameRequest request = new UpdateNicknameRequest(newNickname);
        Member member = Member.builder()
                .id(memberId).socialId("social-id").socialType(SocialType.GOOGLE).nickname("기존닉네임").build();
        given(memberRepository.getByMemberId(memberId)).willReturn(member);
        given(memberRepository.existsByNickname(newNickname)).willReturn(true);

        assertThatThrownBy(() -> authService.updateNickname(memberId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATED_NICKNAME);
        verify(memberRepository, never()).flush();
    }

    @Test
    @DisplayName("updateNickname: flush 단계 race로 DataIntegrityViolation 발생 시 DUPLICATED_NICKNAME으로 변환")
    void updateNickname_raceCondition() {
        Long memberId = 1L;
        String newNickname = "우주탐험가";
        UpdateNicknameRequest request = new UpdateNicknameRequest(newNickname);
        Member member = Member.builder()
                .id(memberId).socialId("social-id").socialType(SocialType.GOOGLE).nickname("기존닉네임").build();
        given(memberRepository.getByMemberId(memberId)).willReturn(member);
        given(memberRepository.existsByNickname(newNickname)).willReturn(false);
        willThrow(new DataIntegrityViolationException("uk_nickname"))
                .given(memberRepository).flush();

        assertThatThrownBy(() -> authService.updateNickname(memberId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATED_NICKNAME);
    }

    @Test
    @DisplayName("updateNickname: 회원이 없으면 MEMBER_NOT_FOUND")
    void updateNickname_memberNotFound() {
        Long memberId = 1L;
        String newNickname = "우주탐험가";
        UpdateNicknameRequest request = new UpdateNicknameRequest(newNickname);
        given(memberRepository.getByMemberId(memberId)).willThrow(new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        assertThatThrownBy(() -> authService.updateNickname(memberId, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        verify(memberRepository, never()).existsByNickname(any());
    }

    // ===== withdraw =====

    @Test
    @DisplayName("withdraw: Member 존재 시 DB 삭제(+CASCADE) + Firebase 사용자 삭제")
    void withdraw_success() throws Exception {
        Long memberId = 1L;
        String socialId = "firebase-uid-123";
        Member member = Member.builder()
                .id(memberId).socialId(socialId).socialType(SocialType.GOOGLE).nickname("탈퇴할회원").build();
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        authService.withdraw(memberId);

        verify(memberRepository).delete(member);
        verify(firebaseAuth).deleteUser(socialId);
        // user_devices는 FK CASCADE로 자동 삭제되므로 AuthService가 직접 호출하지 않는다
        then(userDeviceRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("withdraw: Member 이미 없으면 멱등 처리 (아무 호출 없음)")
    void withdraw_alreadyWithdrawn() throws Exception {
        Long memberId = 1L;
        given(memberRepository.findById(memberId)).willReturn(Optional.empty());

        authService.withdraw(memberId);

        verify(memberRepository, never()).delete(any(Member.class));
        verify(firebaseAuth, never()).deleteUser(any());
        then(userDeviceRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("withdraw: Firebase USER_NOT_FOUND 예외는 무시하고 정상 완료")
    void withdraw_firebaseUserNotFound() throws Exception {
        Long memberId = 1L;
        String socialId = "firebase-uid-123";
        Member member = Member.builder()
                .id(memberId).socialId(socialId).socialType(SocialType.GOOGLE).nickname("탈퇴할회원").build();
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        FirebaseAuthException firebaseEx = mock(FirebaseAuthException.class);
        given(firebaseEx.getAuthErrorCode()).willReturn(AuthErrorCode.USER_NOT_FOUND);
        willThrow(firebaseEx).given(firebaseAuth).deleteUser(socialId);

        authService.withdraw(memberId);

        verify(memberRepository).delete(member);
        verify(firebaseAuth).deleteUser(socialId);
    }

    @Test
    @DisplayName("withdraw: Firebase 일반 오류도 무시하고 정상 완료 (멱등성 유지)")
    void withdraw_firebaseGenericError() throws Exception {
        Long memberId = 1L;
        String socialId = "firebase-uid-123";
        Member member = Member.builder()
                .id(memberId).socialId(socialId).socialType(SocialType.GOOGLE).nickname("탈퇴할회원").build();
        given(memberRepository.findById(memberId)).willReturn(Optional.of(member));

        FirebaseAuthException firebaseEx = mock(FirebaseAuthException.class);
        given(firebaseEx.getAuthErrorCode()).willReturn(AuthErrorCode.CERTIFICATE_FETCH_FAILED);
        given(firebaseEx.getMessage()).willReturn("Firebase 일시 장애");
        willThrow(firebaseEx).given(firebaseAuth).deleteUser(socialId);

        authService.withdraw(memberId);

        verify(memberRepository).delete(member);
        verify(firebaseAuth).deleteUser(socialId);
    }
}
