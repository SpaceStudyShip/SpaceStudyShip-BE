package com.elipair.spacestudyship.controller.auth;

import com.elipair.spacestudyship.auth.dto.CheckNicknameRequest;
import com.elipair.spacestudyship.auth.dto.CheckNicknameResponse;
import com.elipair.spacestudyship.auth.dto.LoginRequest;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameRequest;
import com.elipair.spacestudyship.auth.dto.UpdateNicknameResponse;
import com.elipair.spacestudyship.auth.dto.LoginResponse;
import com.elipair.spacestudyship.auth.dto.LogoutRequest;
import com.elipair.spacestudyship.auth.dto.ReissueRequest;
import com.elipair.spacestudyship.auth.dto.ReissueResponse;
import com.elipair.spacestudyship.auth.interceptor.AuthMember;
import com.elipair.spacestudyship.auth.interceptor.LoginMember;
import com.elipair.spacestudyship.auth.service.AuthService;
import com.elipair.spacestudyship.common.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "소셜 로그인 및 토큰 관리 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "소셜 로그인")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        LoginResponse response = authService.login(request);
        if (response.isNewMember()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "토큰 재발급")
    @PostMapping("/reissue")
    public ResponseEntity<ReissueResponse> reissue(@RequestBody @Valid ReissueRequest request) {
        return ResponseEntity.ok(authService.reissue(request));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "닉네임 중복 확인")
    @GetMapping("/check-nickname")
    public ResponseEntity<CheckNicknameResponse> checkNickname(
            @AuthMember LoginMember loginMember,
            @Valid @ModelAttribute CheckNicknameRequest request) {
        return ResponseEntity.ok(authService.checkNickname(request.nickname()));
    }

    @Operation(summary = "닉네임 변경")
    @PatchMapping("/nickname")
    public ResponseEntity<UpdateNicknameResponse> updateNickname(
            @AuthMember LoginMember loginMember,
            @RequestBody @Valid UpdateNicknameRequest request) {
        return ResponseEntity.ok(authService.updateNickname(loginMember.memberId(), request));
    }

    @Operation(
            summary = "회원 탈퇴",
            description = """
                    인증된 사용자의 계정과 관련 데이터를 영구 삭제합니다. **이 작업은 되돌릴 수 없습니다.**

                    ### 삭제 대상
                    - `members` 테이블의 해당 회원 row
                    - Redis 의 `refresh_token:{memberId}` 키 (모든 디바이스 세션 무효화)
                    - Firebase Authentication 의 해당 사용자 (uid = 회원의 socialId)

                    ### 처리 순서
                    1. 회원 row 삭제 (`@Transactional`)
                    2. Redis refresh token 삭제
                    3. Firebase Authentication 사용자 삭제

                    ### 멱등성
                    - 동일한 토큰으로 두 번 호출되거나, 다른 디바이스에서 먼저 탈퇴되어 회원이 이미 없는 상태에서 호출되어도 동일하게 **204**를 응답합니다.
                    - Firebase 측에서 사용자가 이미 없는 경우(`USER_NOT_FOUND`)도 무시하고 정상 완료 처리합니다.
                    - Firebase 일시 장애 등 외부 시스템 오류도 서버에서 로그만 남기고 클라이언트에는 **204**를 응답합니다 (우리 측 데이터 정리는 이미 완료).

                    ### 클라이언트 처리 가이드
                    - 응답 받은 후 로컬에 저장된 Access Token / Refresh Token / 회원 정보를 모두 삭제하고 로그인 화면으로 이동하세요.
                    - 네트워크 오류로 응답을 못 받은 경우 재시도 가능합니다 (멱등 보장).
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "탈퇴 성공. 응답 본문 없음. (이미 탈퇴된 상태 / Firebase 측 사용자 부재 / 외부 시스템 일시 오류 등 모두 포함)",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 — Access Token 이 헤더에 없거나, 만료되었거나, 유효하지 않은 경우. 클라이언트는 `/api/auth/reissue` 로 재발급을 시도해야 합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "UnauthenticatedRequest",
                                    value = """
                                            {
                                              "code": "UNAUTHENTICATED_REQUEST",
                                              "message": "로그인이 필요합니다."
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류. 주로 DB 통신 실패 시. 사용자에게는 \"잠시 후 다시 시도해주세요\" 안내가 적절합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "InternalServerError",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "서버 내부 오류가 발생했습니다."
                                            }
                                            """
                            )
                    )
            )
    })
    @DeleteMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@AuthMember LoginMember loginMember) {
        authService.withdraw(loginMember.memberId());
        return ResponseEntity.noContent().build();
    }
}
